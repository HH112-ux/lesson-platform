package com.lp.admin.service;

import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;

/**
 * Operations course review use cases exposed to the console.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface AdminCourseReviewService {

    /**
     * Lists the courses waiting for review.
     *
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of pending courses
     * @author Caspian
     * @since 0.1.0
     */
    AdminPendingCoursePageView pending(int pageNum, int pageSize);

    /**
     * Approves or rejects a pending course.
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
}
