package com.lp.auth.dto;

/**
 * Trade-side blockers returned by the trade-service internal API.
 *
 * <p>Deserialized from the trade envelope; Jackson binds records through the constructor because
 * the build keeps parameter names.
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
