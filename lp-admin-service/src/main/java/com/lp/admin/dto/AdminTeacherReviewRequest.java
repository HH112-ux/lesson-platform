package com.lp.admin.dto;

/**
 * Onboarding review decision submitted by an operator.
 *
 * @param approved {@code true} to approve, {@code false} to reject
 * @param remark rejection remark, required when rejecting
 * @author Caspian
 * @since 0.5.0
 */
public record AdminTeacherReviewRequest(boolean approved, String remark) {}
