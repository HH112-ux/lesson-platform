package com.lp.course.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.entity.Course;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.service.TeacherCourseService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Teacher console implementation backed by {@link TeacherCourseMapper}.
 *
 * <p>Editability follows the course state machine: only drafts and offline courses can change.
 * Editing an offline course returns it to draft in the same SQL statement, so the teacher must
 * submit it again and operations review the new content. Pending and published courses are read
 * only for the teacher.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class TeacherCourseServiceImpl implements TeacherCourseService {

    /** Draft status. */
    private static final int STATUS_DRAFT = 0;

    /** Status of a course taken offline by operations. */
    private static final int STATUS_OFFLINE = 3;

    /** Maximum page size accepted by the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum title length, matching {@code course.title}. */
    private static final int MAX_TITLE_LENGTH = 120;

    /** Maximum subtitle length, matching {@code course.subtitle}. */
    private static final int MAX_SUBTITLE_LENGTH = 200;

    /** Maximum cover URL length, matching {@code course.cover_url}. */
    private static final int MAX_COVER_LENGTH = 255;

    private final TeacherCourseMapper teacherCourseMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param teacherCourseMapper teacher scoped course statements
     * @author Caspian
     * @since 0.1.0
     */
    public TeacherCourseServiceImpl(TeacherCourseMapper teacherCourseMapper) {
        this.teacherCourseMapper = teacherCourseMapper;
    }

    /**
     * Lists one page of the caller's courses.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param status optional course status filter
     * @param page one based page number
     * @param size page size, capped at 100
     * @return one page of owned courses
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCoursePageView list(
            long userId, String roles, Integer status, int page, int size) {
        Actor actor = resolveActor(userId, roles);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (status != null && (status < STATUS_DRAFT || status > STATUS_OFFLINE)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status is out of range");
        }
        long total = teacherCourseMapper.countByOwner(actor.teacherId(), status);
        if (total == 0) {
            return new TeacherCoursePageView(List.of(), 0, safePage, safeSize);
        }
        List<TeacherCourseView> items =
                teacherCourseMapper
                        .selectPageByOwner(
                                actor.teacherId(),
                                status,
                                (long) (safePage - 1) * safeSize,
                                safeSize)
                        .stream()
                        .map(this::toView)
                        .toList();
        return new TeacherCoursePageView(items, total, safePage, safeSize);
    }

    /**
     * Creates a draft course owned by the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param request course form
     * @return the created course
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView create(long userId, String roles, TeacherCourseRequest request) {
        Actor actor = resolveActor(userId, roles);
        if (actor.teacherId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        Course course = new Course();
        course.setOwnerTeacherId(actor.teacherId());
        apply(course, request);
        if (course.getCategoryId() == null
                || isBlank(course.getTitle())
                || isBlank(course.getCoverUrl())
                || course.getPrice() == null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "category, title, cover and price are required");
        }
        teacherCourseMapper.insert(course);
        return toView(teacherCourseMapper.selectById(course.getId()));
    }

    /**
     * Edits a draft or offline course; editing an offline course sends it back to draft.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request course form, omitted fields keep their current value
     * @return the course after the update
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView update(
            long userId, String roles, long courseId, TeacherCourseRequest request) {
        Actor actor = resolveActor(userId, roles);
        Course course = requireAccessible(actor, courseId);
        requireEditable(course);
        apply(course, request);
        if (teacherCourseMapper.updateEditable(course) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "course is no longer editable");
        }
        return toView(teacherCourseMapper.selectById(courseId));
    }

    /**
     * Loads one course visible to the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return course detail
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView detail(long userId, String roles, long courseId) {
        return toView(requireAccessible(resolveActor(userId, roles), courseId));
    }

    /**
     * Moves a draft or offline course into the review queue.
     *
     * <p>A course is submittable only when at least one lesson carries a video object key and a
     * positive duration, otherwise operations would have nothing to review.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return the course in pending-review status
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView submit(long userId, String roles, long courseId) {
        Actor actor = resolveActor(userId, roles);
        Course course = requireAccessible(actor, courseId);
        requireEditable(course);
        if (teacherCourseMapper.countSubmittableLessons(courseId) == 0) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "at least one lesson with video and duration is required");
        }
        if (teacherCourseMapper.submit(courseId, LocalDateTime.now()) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "course is no longer submittable");
        }
        return toView(teacherCourseMapper.selectById(courseId));
    }

    /**
     * Applies the non-null fields of the request onto the course row and validates each one.
     *
     * @param course target row, mutated in place
     * @param request submitted values
     * @author Caspian
     * @since 0.1.0
     */
    private void apply(Course course, TeacherCourseRequest request) {
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "title length is invalid");
            }
            course.setTitle(title);
        }
        if (request.subtitle() != null) {
            String subtitle = request.subtitle().trim();
            if (subtitle.length() > MAX_SUBTITLE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "subtitle is too long");
            }
            course.setSubtitle(subtitle.isEmpty() ? null : subtitle);
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.coverUrl() != null) {
            String coverUrl = request.coverUrl().trim();
            if (coverUrl.isEmpty() || coverUrl.length() > MAX_COVER_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "coverUrl length is invalid");
            }
            course.setCoverUrl(coverUrl);
        }
        if (request.categoryId() != null) {
            if (request.categoryId() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "categoryId must be positive");
            }
            course.setCategoryId(request.categoryId());
        }
        if (request.price() != null) {
            course.setPrice(requireMoney(request.price(), "price"));
        }
        if (request.originalPrice() != null) {
            course.setOriginalPrice(requireMoney(request.originalPrice(), "originalPrice"));
        }
        if (request.level() != null) {
            if (request.level() < 1 || request.level() > 3) {
                throw new BizException(ErrorCode.PARAM_INVALID, "level is out of range");
            }
            course.setLevel(request.level());
        }
        if (course.getLevel() == null) {
            course.setLevel(1);
        }
    }

    /**
     * Validates a money amount: non-negative and with at most two decimals.
     *
     * @param value amount in yuan
     * @param field field name used in the error message
     * @return the amount unchanged
     * @author Caspian
     * @since 0.1.0
     */
    private BigDecimal requireMoney(BigDecimal value, String field) {
        if (value.signum() < 0 || value.movePointRight(2).stripTrailingZeros().scale() > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is invalid");
        }
        return value;
    }

    /**
     * Resolves the caller into a teacher identity and admin flag.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return resolved actor
     * @author Caspian
     * @since 0.1.0
     */
    private Actor resolveActor(long userId, String roles) {
        boolean admin = hasRole(roles, "ADMIN");
        if (!admin && !hasRole(roles, "TEACHER")) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Long teacherId = teacherCourseMapper.selectTeacherIdByUser(userId);
        if (teacherId == null && !admin) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        return new Actor(teacherId, admin);
    }

    /**
     * Loads a course and checks that the caller may see it.
     *
     * @param actor resolved caller
     * @param courseId course primary key
     * @return course row
     * @author Caspian
     * @since 0.1.0
     */
    private Course requireAccessible(Actor actor, long courseId) {
        Course course = teacherCourseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!actor.admin() && !Objects.equals(course.getOwnerTeacherId(), actor.teacherId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return course;
    }

    /**
     * Rejects changes to courses in a status the teacher cannot edit.
     *
     * @param course current row
     * @author Caspian
     * @since 0.1.0
     */
    private void requireEditable(Course course) {
        if (course.getStatus() != STATUS_DRAFT && course.getStatus() != STATUS_OFFLINE) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, "only draft or offline courses are editable");
        }
    }

    /**
     * Checks whether a role list contains the given role.
     *
     * @param roles comma separated roles, may be {@code null}
     * @param expected role to look for
     * @return {@code true} when present
     * @author Caspian
     * @since 0.1.0
     */
    private boolean hasRole(String roles, String expected) {
        if (roles == null) {
            return false;
        }
        for (String role : roles.split(",")) {
            if (expected.equals(role.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a string is {@code null} or blank.
     *
     * @param value text
     * @return {@code true} when absent
     * @author Caspian
     * @since 0.1.0
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Maps a row to the console projection, converting yuan prices to fen.
     *
     * @param course course row
     * @return console view
     * @author Caspian
     * @since 0.1.0
     */
    private TeacherCourseView toView(Course course) {
        Long originalPriceFen =
                course.getOriginalPrice() == null
                        ? null
                        : course.getOriginalPrice().movePointRight(2).longValueExact();
        return new TeacherCourseView(
                course.getId(),
                course.getTitle(),
                course.getSubtitle(),
                course.getCoverUrl(),
                course.getDescription(),
                course.getPrice().movePointRight(2).longValueExact(),
                originalPriceFen,
                course.getCategoryId(),
                course.getLevel() == null ? 1 : course.getLevel(),
                course.getStatus(),
                course.getReviewRemark(),
                course.getLessonCount() == null ? 0L : course.getLessonCount(),
                course.getSubmittedAt(),
                course.getUpdatedAt());
    }

    /**
     * Resolved caller identity.
     *
     * @param teacherId {@code teacher_profile.id}, {@code null} for an administrator without one
     * @param admin whether the caller holds the {@code ADMIN} role
     * @author Caspian
     * @since 0.1.0
     */
    private record Actor(Long teacherId, boolean admin) {}
}
