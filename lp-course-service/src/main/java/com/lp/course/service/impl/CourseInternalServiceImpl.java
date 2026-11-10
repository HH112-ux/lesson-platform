package com.lp.course.service.impl;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.money.Money;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CourseReviewAuditView;
import com.lp.course.dto.CourseReviewResultView;
import com.lp.course.dto.CourseStatsView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.dto.LessonPlayView;
import com.lp.course.dto.PendingCoursePageView;
import com.lp.course.dto.PendingCourseView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.CourseReviewAudit;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.mapper.CourseReviewAuditMapper;
import com.lp.course.service.AdminAuditService;
import com.lp.course.service.CourseInternalService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link CourseInternalService} backed by {@link CourseMapper}.
 *
 * <p>Prices are converted from yuan to fen exactly; rounding is never applied because a fraction of
 * a fen would signal corrupt pricing data instead.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class CourseInternalServiceImpl implements CourseInternalService {

    private final CourseMapper courseMapper;

    private final CourseReviewAuditMapper courseReviewAuditMapper;

    private final AdminAuditService adminAuditService;

    /** Audit action of an approved course. */
    private static final int ACTION_APPROVE = 1;

    /** Audit action of a rejected course. */
    private static final int ACTION_REJECT = 2;

    /** Course status of a submitted course waiting for the operations review queue. */
    private static final int STATUS_PENDING_REVIEW = 1;

    /** Resulting status of an approved course. */
    private static final int STATUS_PUBLISHED = 2;

    /** Resulting status of a rejected course, sent back to draft. */
    private static final int STATUS_DRAFT = 0;

    /** Status of a course taken offline. */
    private static final int STATUS_OFFLINE = 3;

    /** Maximum review page size. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum rejection remark length, matching {@code course.review_remark}. */
    private static final int MAX_REMARK_LENGTH = 300;

    /**
     * Creates the service with its mapper dependencies.
     *
     * @param courseMapper mapper reading the {@code lp_cms} course tables
     * @param courseReviewAuditMapper mapper appending review audit entries
     * @param adminAuditService unified audit appender, joined to the review transaction
     * @author Caspian
     * @since 0.1.0
     */
    public CourseInternalServiceImpl(
            CourseMapper courseMapper,
            CourseReviewAuditMapper courseReviewAuditMapper,
            AdminAuditService adminAuditService) {
        this.courseMapper = courseMapper;
        this.courseReviewAuditMapper = courseReviewAuditMapper;
        this.adminAuditService = adminAuditService;
    }

    /**
     * Loads one course or fails with {@code 40401}.
     *
     * @param courseId course primary key
     * @return course projection
     * @throws BizException when the course is absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseInternalView course(long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toView(course);
    }

    /**
     * Loads a batch of courses; unknown ids are silently omitted.
     *
     * @param courseIds course primary keys, may be empty
     * @return projections in database order
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<CourseInternalView> courses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectByIds(courseIds).stream().map(this::toView).toList();
    }

    /**
     * Lists the visible lessons of a course or fails with {@code 40401}.
     *
     * @param courseId course primary key
     * @return lesson outline
     * @throws BizException when the course is absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<LessonInternalView> lessons(long courseId) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return courseMapper.selectLessons(courseId).stream().map(this::toView).toList();
    }

    /**
     * Loads one playable lesson or fails with {@code 40401}.
     *
     * @param lessonId lesson primary key
     * @return lesson with media key and duration
     * @throws BizException when the lesson is absent, hidden or deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public LessonPlayView lessonForPlay(long lessonId) {
        CourseLesson lesson = courseMapper.selectPlayableById(lessonId);
        if (lesson == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new LessonPlayView(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getVideoUrl(),
                lesson.getVideoDurationSec() == null ? 0 : lesson.getVideoDurationSec(),
                lesson.getIsFree() != null && lesson.getIsFree() == 1,
                lesson.getCourseStatus() == null ? STATUS_DRAFT : lesson.getCourseStatus());
    }

    /**
     * Lists the courses waiting for an operations review decision.
     *
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of pending courses, oldest submission first
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PendingCoursePageView pendingCourses(int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = courseMapper.countByStatus(STATUS_PENDING_REVIEW);
        if (total == 0) {
            return new PendingCoursePageView(List.of(), 0, safePage, safeSize);
        }
        List<PendingCourseView> records =
                courseMapper.selectPageByStatus(
                        STATUS_PENDING_REVIEW, (long) (safePage - 1) * safeSize, safeSize);
        return new PendingCoursePageView(records, total, safePage, safeSize);
    }

    /**
     * Approves or rejects a pending course; approval publishes it in the same step.
     *
     * @param courseId course primary key
     * @param approved {@code true} to publish, {@code false} to send back to draft
     * @param priceFen price in fen set on approval, {@code null} keeps the current price
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @param traceId request trace id written to the unified audit log
     * @return course state after the decision
     * @throws BizException when the course is not pending or the remark is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional
    public CourseReviewResultView review(
            long courseId,
            boolean approved,
            Long priceFen,
            String remark,
            long reviewerId,
            int operatorRole,
            String traceId) {
        if (priceFen != null && !Money.isStorable(priceFen)) {
            // A negative or oversized price would be clamped to a free order or overflow the
            // column.
            throw new BizException(ErrorCode.PARAM_INVALID, "price is invalid");
        }
        LocalDateTime reviewedAt = LocalDateTime.now();
        String remarkText = null;
        int updated;
        if (approved) {
            BigDecimal price = priceFen == null ? null : BigDecimal.valueOf(priceFen, 2);
            updated = courseMapper.approve(courseId, price, reviewerId, reviewedAt);
        } else {
            String trimmed = remark == null ? "" : remark.trim();
            if (trimmed.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "remark is required on rejection");
            }
            remarkText = trimmed.substring(0, Math.min(trimmed.length(), MAX_REMARK_LENGTH));
            updated = courseMapper.reject(courseId, remarkText, reviewerId, reviewedAt);
        }
        if (updated == 0) {
            throw new BizException(ErrorCode.COURSE_REVIEW_STATE_INVALID);
        }
        CourseReviewAudit audit = new CourseReviewAudit();
        audit.setCourseId(courseId);
        audit.setAction(approved ? ACTION_APPROVE : ACTION_REJECT);
        audit.setFromStatus(STATUS_PENDING_REVIEW);
        audit.setToStatus(approved ? STATUS_PUBLISHED : STATUS_DRAFT);
        audit.setReviewerId(reviewerId);
        audit.setRemark(remarkText);
        audit.setCreatedAt(reviewedAt);
        courseReviewAuditMapper.insert(audit);
        // Same transaction as the status update above: either both the course and the unified
        // audit row commit, or neither does.
        adminAuditService.record(
                new AuditLogCreateRequest(
                        reviewerId,
                        operatorRole,
                        approved ? AuditAction.COURSE_APPROVE : AuditAction.COURSE_REJECT,
                        AuditObject.COURSE,
                        courseId,
                        STATUS_PENDING_REVIEW,
                        approved ? STATUS_PUBLISHED : STATUS_DRAFT,
                        remarkText,
                        traceId,
                        null,
                        reviewedAt));
        long effectivePriceFen =
                approved && priceFen != null ? priceFen : currentPriceFen(courseId);
        return new CourseReviewResultView(
                courseId,
                approved ? STATUS_PUBLISHED : STATUS_DRAFT,
                effectivePriceFen,
                reviewedAt);
    }

    /**
     * Lists the review audit history of one course, newest decision first.
     *
     * @param courseId course primary key
     * @return audit entries, possibly empty
     * @throws BizException when the course is absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<CourseReviewAuditView> reviewAudits(long courseId) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return courseReviewAuditMapper.selectByCourseId(courseId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Returns course counts grouped by status for the operations dashboard.
     *
     * @return course statistics
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseStatsView stats() {
        return new CourseStatsView(
                courseMapper.countAll(),
                courseMapper.countByStatus(STATUS_DRAFT),
                courseMapper.countByStatus(STATUS_PENDING_REVIEW),
                courseMapper.countByStatus(STATUS_PUBLISHED),
                courseMapper.countByStatus(STATUS_OFFLINE));
    }

    /**
     * Reads the current price of a course and converts it to fen.
     *
     * @param courseId course primary key
     * @return price in fen, {@code 0} when the column is missing
     * @author Caspian
     * @since 0.1.0
     */
    private long currentPriceFen(long courseId) {
        BigDecimal price = courseMapper.selectPrice(courseId);
        return price == null ? 0L : Money.toFen(price);
    }

    private CourseInternalView toView(Course course) {
        long lessonCount = course.getLessonCount() == null ? 0L : course.getLessonCount();
        return new CourseInternalView(
                course.getId(),
                course.getTitle(),
                course.getCoverUrl(),
                Money.toFen(course.getPrice()),
                course.getStatus(),
                course.getOwnerTeacherId(),
                lessonCount,
                course.getInstructorName());
    }

    private LessonInternalView toView(CourseLesson lesson) {
        return new LessonInternalView(
                lesson.getId(),
                lesson.getChapterId(),
                lesson.getTitle(),
                lesson.getIsFree() != null && lesson.getIsFree() == 1,
                lesson.getSort() == null ? 0 : lesson.getSort());
    }

    private CourseReviewAuditView toView(CourseReviewAudit audit) {
        return new CourseReviewAuditView(
                audit.getId(),
                audit.getCourseId(),
                audit.getAction(),
                audit.getFromStatus(),
                audit.getToStatus(),
                audit.getReviewerId(),
                audit.getRemark(),
                audit.getCreatedAt());
    }
}
