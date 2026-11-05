package com.lp.trade.domain;

/**
 * Order status values stored in {@code trade_order.status}.
 *
 * <p>Values follow {@code docs/engineering-plan.md} section 4.3.3 and are shared by both payment
 * channels. Business code must reference these constants instead of raw numbers.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class OrderStatus {

    /** Waiting for payment; expires after the configured deadline. */
    public static final int PENDING = 0;

    /** Paid; the buyer owns the course. */
    public static final int PAID = 1;

    /** Cancelled by the buyer before payment. */
    public static final int CANCELLED = 2;

    /** Closed by the timeout job because payment never completed. */
    public static final int CLOSED = 3;

    /** Refund requested and under review. */
    public static final int REFUNDING = 4;

    /** Refund completed; ownership is revoked. */
    public static final int REFUNDED = 5;

    private OrderStatus() {}
}
