package com.lp.trade.domain;

/**
 * Payment record status values stored in {@code payment_record.status}.
 *
 * <p>The persisted values follow {@code docs/engineering-plan.md} section 4.3.3. The logical flow
 * {@code created -> paying -> paid/closed} maps as follows: a newly inserted record is created and
 * still {@link #PENDING}; once a prepay request was built it stays {@link #PENDING} until the
 * channel callback arrives, then it becomes {@link #SUCCESS} or {@link #CLOSED}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class PaymentStatus {

    /** Waiting for the channel callback. */
    public static final int PENDING = 0;

    /** Channel confirmed the payment. */
    public static final int SUCCESS = 1;

    /** Closed before any successful callback. */
    public static final int CLOSED = 2;

    /**
     * Channel confirmed the payment after the order was closed and the attempt was discarded.
     *
     * <p>This state never grants ownership. It is persisted so operations can find the money and
     * drive a manual refund, and so a repeated callback is recognised as already recorded.
     */
    public static final int PAID_AFTER_CLOSE = 6;

    private PaymentStatus() {}
}
