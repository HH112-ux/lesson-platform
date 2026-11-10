package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * One course review decision exposed by the internal audit query.
 *
 * @param auditId audit primary key
 * @param courseId reviewed course id
 * @param action 1 approved, 2 rejected, 3 withdrawn by the owning teacher
 * @param fromStatus course status before the decision
 * @param toStatus course status after the decision
 * @param reviewerId operator user id
 * @param remark review remark, {@code null} on approval or teacher withdrawal
 * @param createdAt decision time
 * @author Caspian
 * @since 0.1.0
 */
public record CourseReviewAuditView(
        long auditId,
        long courseId,
        int action,
        int fromStatus,
        int toStatus,
        long reviewerId,
        String remark,
        LocalDateTime createdAt) {}
