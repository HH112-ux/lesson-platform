package com.lp.course.service;

import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CourseReviewAuditView;
import com.lp.course.dto.CourseReviewResultView;
import com.lp.course.dto.CourseStatsView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.dto.LessonPlayView;
import com.lp.course.dto.PendingCoursePageView;
import java.util.List;

/**
 * Read-only course queries exposed to other domain services.
 *
 * <p>This facade is not part of the public API: routes live under {@code /internal/v1/**} and the
 * gateway never forwards them.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CourseInternalService {

    /**
     * Loads one course for trade validation.
     *
     * @param courseId course primary key
     * @return course projection with price in fen and publishing status
     * @author Caspian
     * @since 0.1.0
     */
    CourseInternalView course(long courseId);

    /**
     * Loads a batch of courses, preserving no particular order.
     *
     * @param courseIds course primary keys, may be empty
     * @return projections of the ids that exist, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseInternalView> courses(List<Long> courseIds);

    /**
     * Lists the visible lessons of a course.
     *
     * @param courseId course primary key
     * @return lesson outline ordered by chapter and sort
     * @author Caspian
     * @since 0.1.0
     */
    List<LessonInternalView> lessons(long courseId);

    /**
     * Loads one playable lesson for URL signing.
     *
     * @param lessonId lesson primary key
     * @return lesson with media key and duration
     * @throws com.lp.common.exception.BizException with {@code 40401} when the lesson is absent
     * @author Caspian
     * @since 0.1.0
     */
    LessonPlayView lessonForPlay(long lessonId);

    /**
     * Lists the courses waiting for an operations review decision.
     *
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of pending courses
     * @author Caspian
     * @since 0.1.0
     */
    PendingCoursePageView pendingCourses(int pageNum, int pageSize);

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
     * @throws com.lp.common.exception.BizException when the course is not pending or the remark is
     *     missing
     * @author Caspian
     * @since 0.1.0
     */
    CourseReviewResultView review(
            long courseId,
            boolean approved,
            Long priceFen,
            String remark,
            long reviewerId,
            int operatorRole,
            String traceId);

    /**
     * Lists the review audit history of one course, newest decision first.
     *
     * @param courseId course primary key
     * @return audit entries, possibly empty
     * @throws com.lp.common.exception.BizException with {@code 40401} when the course is absent
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseReviewAuditView> reviewAudits(long courseId);

    /**
     * Returns course counts grouped by status for the operations dashboard.
     *
     * @return course statistics
     * @author Caspian
     * @since 0.1.0
     */
    CourseStatsView stats();
}
