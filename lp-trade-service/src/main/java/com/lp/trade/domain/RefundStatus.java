package com.lp.trade.domain;

/**
 * Refund request status values stored in {@code refund_request.status}.
 *
 * <p>The local channel call commits its result in one transaction, so a visible row is almost
 * always successful; the pending and failed values exist for channel adapters that later report a
 * refund asynchronously.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class RefundStatus {

    /** Refund accepted locally, waiting for the channel. */
    public static final int PENDING = 0;

    /** Channel confirmed the refund; access was revoked through the outbox. */
    public static final int SUCCESS = 1;

    /** Channel rejected the refund; the order stays paid and access stays active. */
    public static final int FAILED = 2;

    private RefundStatus() {}
}
