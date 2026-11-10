package com.lp.admin.client;

import com.lp.admin.dto.AdminTeacherPageView;
import com.lp.admin.dto.AdminTeacherReviewResultView;

/**
 * Port to the teacher onboarding API owned by course-service.
 *
 * @author Caspian
 * @since 0.5.0
 */
public interface TeacherAuditClient {

    /**
     * Loads one page of teacher applications.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of applications
     * @author Caspian
     * @since 0.5.0
     */
    AdminTeacherPageView page(Integer auditStatus, String keyword, int pageNum, int pageSize);

    /**
     * Applies an approval or rejection.
     *
     * @param teacherId profile primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return application state after the decision
     * @author Caspian
     * @since 0.5.0
     */
    AdminTeacherReviewResultView review(
            long teacherId, boolean approved, String remark, long reviewerId, int operatorRole);
}
