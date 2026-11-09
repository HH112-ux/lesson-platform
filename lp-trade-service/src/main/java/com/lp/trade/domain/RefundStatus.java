package com.lp.trade.domain;

/**
 * Refund request status values stored in {@code refund_request.status}.
 *
 * <p>Every application starts as {@link #PENDING_REVIEW}. Operations approve it before the channel
 * is called, so a refund can only move money after a human decision. A rejected or failed request
 * leaves the order paid and lets the buyer apply again; only statuses 0, 1 and 2 block a new
 * application for the same order.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class RefundStatus {

    /** Submitted by the buyer and waiting for an operations decision. */
    public static final int PENDING_REVIEW = 0;

    /** Operations approved the request; the channel refund is in progress. */
    public static final int APPROVED = 1;

    /** Channel confirmed the refund; access was revoked through the outbox. */
    public static final int REFUNDED = 2;

    /** Operations rejected the request; the order stays paid and access stays active. */
    public static final int REJECTED = 3;

    /** The channel refund failed after approval; the order returns to paid for manual handling. */
    public static final int FAILED = 4;

    private RefundStatus() {}
}
