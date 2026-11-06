package com.lp.admin.service.impl;

import com.lp.admin.client.CourseReviewClient;
import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;
import com.lp.admin.service.AdminCourseReviewService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Delegating implementation: course state stays in course-service.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AdminCourseReviewServiceImpl implements AdminCourseReviewService {

    /** Maximum review page size. */
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseReviewClient courseReviewClient;

    /**
     * Creates the service with its course-service port.
     *
     * @param courseReviewClient course review API
     * @author Caspian
     * @since 0.1.0
     */
    public AdminCourseReviewServiceImpl(CourseReviewClient courseReviewClient) {
        this.courseReviewClient = courseReviewClient;
    }

    /**
     * Lists the courses waiting for review with clamped paging.
     *
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of pending courses
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminPendingCoursePageView pending(int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return courseReviewClient.pending(safePage, safeSize);
    }

    /**
     * Approves or rejects a pending course; a rejection must carry a remark.
     *
     * @param courseId course primary key
     * @param approved {@code true} to publish, {@code false} to send back to draft
     * @param priceFen price in fen set on approval, {@code null} keeps the current price
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @return course state after the decision
     * @throws BizException when a rejection carries no remark
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminReviewResultView review(
            long courseId, boolean approved, Long priceFen, String remark, long reviewerId) {
        if (!approved && (remark == null || remark.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "remark is required on rejection");
        }
        return courseReviewClient.review(courseId, approved, priceFen, remark, reviewerId);
    }
}
