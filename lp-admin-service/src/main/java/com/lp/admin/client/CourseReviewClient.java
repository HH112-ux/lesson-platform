package com.lp.admin.client;

import com.lp.admin.dto.AdminCourseReviewAuditView;
import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;
import java.util.List;

/**
 * Read/write port to the course-service review API.
 *
 * <p>admin-service holds no course data of its own: every decision is executed by course-service,
 * which owns the course state machine.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CourseReviewClient {

    /**
     * Loads one page of the review queue.
     *
     * @param pageNum one based page number
     * @param pageSize page size
     * @return pending courses, oldest submission first
     * @author Caspian
     * @since 0.1.0
     */
    AdminPendingCoursePageView pending(int pageNum, int pageSize);

    /**
     * Applies a review decision.
     *
     * @param courseId course primary key
     * @param approved {@code true} to publish, {@code false} to send back to draft
     * @param priceFen price in fen set on approval, {@code null} keeps the current price
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @return course state after the decision
     * @author Caspian
     * @since 0.1.0
     */
    AdminReviewResultView review(
            long courseId, boolean approved, Long priceFen, String remark, long reviewerId);

    /**
     * Lists the review audit history of one course, newest decision first.
     *
     * @param courseId course primary key
     * @return audit entries, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    List<AdminCourseReviewAuditView> reviewAudits(long courseId);
}
