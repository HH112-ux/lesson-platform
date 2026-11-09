package com.lp.admin.dto;

/**
 * Operations refund decision sent by the console.
 *
 * @param approved {@code true} to approve, {@code false} to reject
 * @param rejectReason rejection reason, required when rejecting
 * @author Caspian
 * @since 0.1.0
 */
public record AdminRefundReviewRequest(Boolean approved, String rejectReason) {}
