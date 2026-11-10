package com.lp.course.dto;

/**
 * Internal onboarding review decision.
 *
 * @param approved {@code true} to approve, {@code false} to reject
 * @param remark rejection remark, required when rejecting
 * @param reviewerId operations user id, resolved by admin-service from the gateway header
 * @param operatorRole operator role code, 0 operations, 1 administrator
 * @author Caspian
 * @since 0.5.0
 */
public record TeacherAuditReviewRequest(
        boolean approved, String remark, Long reviewerId, Integer operatorRole) {}
