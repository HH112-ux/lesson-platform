package com.lp.course.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.client.LearnEntitlementClient;
import com.lp.course.dto.CourseCategoryView;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CoursePageView;
import com.lp.course.dto.CourseSyllabusView;
import com.lp.course.dto.PublicChapterView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.dto.PublicLessonView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseCategory;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.CourseCategoryMapper;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.service.CourseInternalService;
import com.lp.course.service.CoursePublicService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Public course queries backed by the same projection as the internal contract.
 *
 * <p>Reusing the internal read keeps a single price conversion path, so the purchase page can never
 * disagree with the order service about the price of the same course.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class CoursePublicServiceImpl implements CoursePublicService {

    /** Maximum page size accepted from the catalog API. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Status of a course that is browsable by every caller. */
    private static final int STATUS_PUBLISHED = 2;

    /**
     * Status of a course taken offline.
     *
     * <p>Offline courses stay readable only for callers that already own them, so the storefront
     * never exposes a downgraded course as a purchasable item.
     */
    private static final int STATUS_OFFLINE = 3;

    private final CourseInternalService courseInternalService;

    private final CourseMapper courseMapper;

    private final CourseCategoryMapper courseCategoryMapper;

    private final LearnEntitlementClient learnEntitlementClient;

    /**
     * Creates the service with its readers.
     *
     * @param courseInternalService shared course projection
     * @param courseMapper catalog page queries
     * @param courseCategoryMapper category queries
     * @param learnEntitlementClient owned course lookup
     * @author Caspian
     * @since 0.1.0
     */
    public CoursePublicServiceImpl(
            CourseInternalService courseInternalService,
            CourseMapper courseMapper,
            CourseCategoryMapper courseCategoryMapper,
            LearnEntitlementClient learnEntitlementClient) {
        this.courseInternalService = courseInternalService;
        this.courseMapper = courseMapper;
        this.courseCategoryMapper = courseCategoryMapper;
        this.learnEntitlementClient = learnEntitlementClient;
    }

    /**
     * Loads one course and strips internal only fields.
     *
     * <p>An offline course is treated exactly like a missing one unless the caller already owns it,
     * so a downgraded course cannot be probed through the public detail endpoint.
     *
     * @param courseId course primary key
     * @param userId current user id, may be {@code null}
     * @return public projection
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PublicCourseView course(long courseId, Long userId) {
        CourseInternalView course = courseInternalService.course(courseId);
        boolean owned = ownsCourse(userId, courseId);
        requireVisible(course.getStatus(), owned);
        return new PublicCourseView(
                course.getCourseId(),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPrice(),
                course.getStatus(),
                course.getLessonCount(),
                course.getInstructorName(),
                owned);
    }

    /**
     * Loads the outline of one course with the same visibility rule as the detail page.
     *
     * <p>Drafts and courses pending review always fail; an offline course stays readable only for a
     * caller that already owns it. Keeping this rule aligned with the detail endpoint prevents the
     * outline from leaking a downgraded course.
     *
     * @param courseId course primary key
     * @param userId current user id, may be {@code null}
     * @return syllabus with a playable flag per lesson
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseSyllabusView syllabus(long courseId, Long userId) {
        CourseInternalView course = courseInternalService.course(courseId);
        boolean owned = ownsCourse(userId, courseId);
        requireVisible(course.getStatus(), owned);
        List<CourseChapter> chapters = courseMapper.selectChapters(courseId);
        Map<Long, List<CourseLesson>> lessonsByChapter = new LinkedHashMap<>();
        for (CourseChapter chapter : chapters) {
            lessonsByChapter.put(chapter.getId(), new ArrayList<>());
        }
        for (CourseLesson lesson : courseMapper.selectLessons(courseId)) {
            List<CourseLesson> bucket = lessonsByChapter.get(lesson.getChapterId());
            if (bucket != null) {
                bucket.add(lesson);
            }
        }
        List<PublicChapterView> chapterViews =
                chapters.stream()
                        .map(
                                chapter ->
                                        new PublicChapterView(
                                                chapter.getId(),
                                                chapter.getTitle(),
                                                chapter.getSort() == null ? 0 : chapter.getSort(),
                                                lessonsByChapter.get(chapter.getId()).stream()
                                                        .map(lesson -> toLessonView(lesson, owned))
                                                        .toList()))
                        .toList();
        return new CourseSyllabusView(courseId, owned, chapterViews);
    }

    /**
     * Lists one page of published courses, newest first.
     *
     * @param category category id or slug
     * @param keyword title/subtitle fragment
     * @param page one based page number
     * @param size page size
     * @param userId current user id, may be {@code null}
     * @return page of published courses
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CoursePageView list(String category, String keyword, int page, int size, Long userId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();

        Long categoryId = resolveCategoryId(category);
        if (categoryId != null && categoryId <= 0) {
            return new CoursePageView(List.of(), 0, safePage, safeSize);
        }
        long total = courseMapper.countPublished(categoryId, safeKeyword);
        if (total == 0) {
            return new CoursePageView(List.of(), 0, safePage, safeSize);
        }
        long offset = (long) (safePage - 1) * safeSize;
        List<Course> rows =
                courseMapper.selectPublishedPage(categoryId, safeKeyword, offset, safeSize);
        Set<Long> owned =
                userId == null
                        ? Set.of()
                        : learnEntitlementClient.ownedCourseIds(
                                userId, rows.stream().map(Course::getId).toList());
        List<PublicCourseView> items =
                rows.stream().map(row -> toView(row, owned.contains(row.getId()))).toList();
        return new CoursePageView(items, total, safePage, safeSize);
    }

    /**
     * Returns the visible taxonomy.
     *
     * @return categories in display order
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<CourseCategoryView> categories() {
        return courseCategoryMapper.selectVisible().stream()
                .map(
                        category ->
                                new CourseCategoryView(
                                        category.getId(), category.getName(), category.getSlug()))
                .toList();
    }

    /**
     * Tells whether the caller owns the course.
     *
     * @param userId current user id, {@code null} when anonymous
     * @param courseId course primary key
     * @return {@code true} when the caller holds an entitlement for the course
     * @author Caspian
     * @since 0.1.0
     */
    private boolean ownsCourse(Long userId, long courseId) {
        return userId != null
                && learnEntitlementClient
                        .ownedCourseIds(userId, List.of(courseId))
                        .contains(courseId);
    }

    /**
     * Enforces the public visibility rule shared by the detail and outline endpoints.
     *
     * <p>Published courses are visible to everyone. An offline course is visible only to an owner,
     * and every other status is invisible, so the caller always receives {@code 40401} instead of a
     * status that would reveal the course still exists.
     *
     * @param status current course status
     * @param owned whether the caller owns the course
     * @throws BizException with {@code 40401} when the course must look absent
     * @author Caspian
     * @since 0.1.0
     */
    private void requireVisible(int status, boolean owned) {
        if (status == STATUS_PUBLISHED || (status == STATUS_OFFLINE && owned)) {
            return;
        }
        throw new BizException(ErrorCode.NOT_FOUND);
    }

    /**
     * Resolves the catalog filter to a category id.
     *
     * @param category numeric id or slug, may be {@code null}
     * @return category id, {@code null} for no filter, or {@code -1} when the filter is unknown
     * @author Caspian
     * @since 0.1.0
     */
    private Long resolveCategoryId(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim();
        CourseCategory found;
        if (value.chars().allMatch(Character::isDigit)) {
            found = courseCategoryMapper.selectVisibleById(Long.parseLong(value));
        } else {
            found = courseCategoryMapper.selectVisibleBySlug(value);
        }
        return found == null ? -1L : found.getId();
    }

    /**
     * Maps a lesson row to the syllabus row.
     *
     * @param lesson lesson row
     * @param owned whether the caller owns the course
     * @return syllabus lesson with the playable flag resolved for the caller
     * @author Caspian
     * @since 0.1.0
     */
    private PublicLessonView toLessonView(CourseLesson lesson, boolean owned) {
        boolean free = lesson.getIsFree() != null && lesson.getIsFree() == 1;
        return new PublicLessonView(
                lesson.getId(),
                lesson.getTitle(),
                free,
                lesson.getVideoDurationSec() == null ? 0 : lesson.getVideoDurationSec(),
                lesson.getSort() == null ? 0 : lesson.getSort(),
                owned || free);
    }

    /**
     * Maps a catalog row to the public card; the price conversion mirrors the internal reader.
     *
     * @param course catalog row
     * @param owned whether the caller owns the course
     * @return public card
     * @author Caspian
     * @since 0.1.0
     */
    private PublicCourseView toView(Course course, boolean owned) {
        return new PublicCourseView(
                course.getId(),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPrice().movePointRight(2).longValueExact(),
                course.getStatus(),
                course.getLessonCount() == null ? 0L : course.getLessonCount(),
                course.getInstructorName(),
                owned);
    }
}
