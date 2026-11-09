package com.lp.learn.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.client.CourseInternalClient;
import com.lp.learn.client.CourseSnapshot;
import com.lp.learn.domain.EntitlementStatus;
import com.lp.learn.dto.CourseProgressSummaryView;
import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.LearningProgressMapper;
import com.lp.learn.mapper.UserEntitlementMapper;
import com.lp.learn.service.MyCourseService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Default {@link MyCourseService} implementation.
 *
 * <p>Entitlements are the source of truth for access; course titles and covers come from the
 * course-service internal batch API. Entitlements whose course disappeared are skipped instead of
 * failing the whole page. Refunded entitlements stay in the list with {@code refunded = true}: the
 * student keeps reading progress, but the course is no longer accessible.
 *
 * <p>{@code progressPercent} is the completed lesson count divided by the live lesson count and
 * rounded half up to an integer, capped at 100. A course without live lessons reports 0. {@code
 * lastLessonId} is the lesson of the most recently updated progress row, or {@code null} before the
 * first report.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class MyCourseServiceImpl implements MyCourseService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int FULL_PERCENT = 100;

    private final UserEntitlementMapper userEntitlementMapper;

    private final LearningProgressMapper learningProgressMapper;

    private final CourseInternalClient courseInternalClient;

    /**
     * Creates the service with its dependencies.
     *
     * @param userEntitlementMapper entitlement persistence
     * @param learningProgressMapper progress reader
     * @param courseInternalClient course display reader
     * @author Caspian
     * @since 0.1.0
     */
    public MyCourseServiceImpl(
            UserEntitlementMapper userEntitlementMapper,
            LearningProgressMapper learningProgressMapper,
            CourseInternalClient courseInternalClient) {
        this.userEntitlementMapper = userEntitlementMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.courseInternalClient = courseInternalClient;
    }

    /**
     * Returns one page of courses the student may access.
     *
     * @param userId student user id
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return paged courses; empty when the student owns nothing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PageView<MyCourseView> myCourses(long userId, int pageNum, int pageSize) {
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = userEntitlementMapper.countAllByUser(userId);
        if (total == 0) {
            return new PageView<>(safePageNum, safePageSize, 0, List.of());
        }

        List<UserEntitlement> entitlements =
                userEntitlementMapper.selectAllByUser(
                        userId, (long) (safePageNum - 1) * safePageSize, safePageSize);
        if (entitlements.isEmpty()) {
            return new PageView<>(safePageNum, safePageSize, total, List.of());
        }

        List<Long> courseIds =
                entitlements.stream().map(UserEntitlement::getCourseId).distinct().toList();
        Map<Long, CourseSnapshot> courses =
                courseInternalClient.courses(courseIds).stream()
                        .collect(
                                Collectors.toMap(CourseSnapshot::getCourseId, Function.identity()));
        Map<Long, Integer> completedCounts = new HashMap<>();
        Map<Long, Long> lastLessonIds = new HashMap<>();
        for (LearningProgress progress :
                learningProgressMapper.selectByUserCourses(userId, courseIds)) {
            lastLessonIds.putIfAbsent(progress.getCourseId(), progress.getLessonId());
            if (progress.getCompleted() != null && progress.getCompleted() == 1) {
                completedCounts.merge(progress.getCourseId(), 1, Integer::sum);
            }
        }
        List<MyCourseView> records =
                entitlements.stream()
                        .map(
                                entitlement -> {
                                    CourseSnapshot course = courses.get(entitlement.getCourseId());
                                    if (course == null) {
                                        return null;
                                    }
                                    return new MyCourseView(
                                            course.getCourseId(),
                                            course.getTitle(),
                                            course.getCoverUrl(),
                                            percent(
                                                    completedCounts.getOrDefault(
                                                            course.getCourseId(), 0),
                                                    course.getLessonCount()),
                                            lastLessonIds.get(course.getCourseId()),
                                            Integer.valueOf(EntitlementStatus.REVOKED)
                                                    .equals(entitlement.getStatus()));
                                })
                        .filter(view -> view != null)
                        .toList();
        return new PageView<>(safePageNum, safePageSize, total, records);
    }

    private static int percent(int completedLessons, int lessonCount) {
        if (lessonCount <= 0) {
            return 0;
        }
        long rounded = Math.round(completedLessons * (double) FULL_PERCENT / lessonCount);
        return (int) Math.min(rounded, FULL_PERCENT);
    }

    /**
     * Returns the completed share of one student in one course for refund admission.
     *
     * @param userId student user id
     * @param courseId course id
     * @return completed lessons, live lesson count and rounded percentage
     * @throws BizException with {@code 40401} when the course is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseProgressSummaryView progressSummary(long userId, long courseId) {
        List<CourseSnapshot> courses = courseInternalClient.courses(List.of(courseId));
        if (courses.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "course not found");
        }
        int lessonCount = courses.get(0).getLessonCount();
        int completedLessons = 0;
        for (LearningProgress progress :
                learningProgressMapper.selectByUserCourse(userId, courseId)) {
            if (progress.getCompleted() != null && progress.getCompleted() == 1) {
                completedLessons++;
            }
        }
        return new CourseProgressSummaryView(
                completedLessons, lessonCount, percent(completedLessons, lessonCount));
    }
}
