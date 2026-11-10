package com.lp.trade.dto;

/**
 * Trade-side blockers reported to auth-service before an account cancellation is accepted.
 *
 * <p>The counts are diagnostic only; the decision is carried by {@code blocked} so both services
 * agree on it even if the status sets diverge later.
 *
 * @param userId account owner
 * @param pendingOrders orders in status 0 or 4
 * @param refundingRequests refund requests in status 0, 1 or 5
 * @param blocked {@code true} when a cancellation must be refused
 * @author Caspian
 * @since 0.2.0
 */
public record AccountDeletionBlockerView(
        long userId, int pendingOrders, int refundingRequests, boolean blocked) {}
