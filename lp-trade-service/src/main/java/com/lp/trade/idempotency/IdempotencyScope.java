package com.lp.trade.idempotency;

import java.time.Duration;

/**
 * Write endpoints covered by the idempotency contract.
 *
 * <p>The scope separates the key space, so the same client generated key can be reused across
 * endpoints without collisions. Each scope owns the lifetime of its cached success payload.
 *
 * @author Caspian
 * @since 0.4.0
 */
public enum IdempotencyScope {

    /** {@code POST /api/v1/orders}; the order survives for a day of client retries. */
    ORDER_CREATE("order-create", Duration.ofHours(24)),

    /**
     * {@code POST /api/v1/payments/{orderNo}/pay}; the prepay payload expires with the order
     * payment window, so it is cached for thirty minutes only.
     */
    PAYMENT_INITIATE("payment-initiate", Duration.ofMinutes(30)),

    /** {@code POST /api/v1/orders/{orderId}/refund}; matches the order replay window. */
    REFUND_APPLY("refund-apply", Duration.ofHours(24));

    private final String slug;

    private final Duration resultTtl;

    IdempotencyScope(String slug, Duration resultTtl) {
        this.slug = slug;
        this.resultTtl = resultTtl;
    }

    /**
     * Returns the stable token used inside Redis keys and metric tags.
     *
     * @return lowercase hyphenated scope name
     * @author Caspian
     * @since 0.4.0
     */
    public String slug() {
        return slug;
    }

    /**
     * Returns how long the first successful payload is replayed.
     *
     * @return cache lifetime of the result key
     * @author Caspian
     * @since 0.4.0
     */
    public Duration resultTtl() {
        return resultTtl;
    }
}
