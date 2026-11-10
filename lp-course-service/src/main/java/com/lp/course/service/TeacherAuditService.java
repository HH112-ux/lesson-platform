package com.lp.course.service;

import com.lp.course.dto.TeacherAuditApplicationView;
import com.lp.course.dto.TeacherAuditPageView;
import com.lp.course.dto.TeacherAuditResultView;

/**
 * Teacher onboarding review use cases.
 *
 * <p>Operations lists and decides applications; teachers read their own status and resubmit after a
 * rejection. Only an approved profile may create or edit courses, which the teacher course service
 * enforces independently.
 *
 * @author Caspian
 * @since 0.5.0
 */
public interface TeacherAuditService {

    /**
     * Lists one page of teacher applications for operations.
     *
     * @param auditStatus optional audit status filter, {@code 0} pending, {@code 1} approved,
     *     {@code 2} rejected
     * @param keyword optional match on the display name
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of applications
     * @author Caspian
     * @since 0.5.0
     */
    TeacherAuditPageView page(Integer auditStatus, String keyword, int pageNum, int pageSize);

    /**
     * Approves or rejects one pending application.
     *
     * @param teacherId profile primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @param traceId request trace id written to the unified audit log
     * @return application state after the decision
     * @author Caspian
     * @since 0.5.0
     */
    TeacherAuditResultView review(
            long teacherId,
            boolean approved,
            String remark,
            long reviewerId,
            int operatorRole,
            String traceId);

    /**
     * Returns the caller's own application.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return the caller's application
     * @author Caspian
     * @since 0.5.0
     */
    TeacherAuditApplicationView application(long userId, String roles);

    /**
     * Puts the caller's rejected application back into the review queue.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return the application after resubmission
     * @author Caspian
     * @since 0.5.0
     */
    TeacherAuditApplicationView resubmit(long userId, String roles);
}
