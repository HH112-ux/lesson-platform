package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund result returned to the buyer.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefundView {

    private final String refundNo;

    private final long orderId;

    private final String orderNo;

    private final BigDecimal amount;

    private final int status;

    private final LocalDateTime refundedAt;

    /**
     * Creates the projection.
     *
     * @param refundNo refund number, also the channel idempotency key
     * @param orderId order id
     * @param orderNo business order number
     * @param amount refunded amount in yuan
     * @param status status from {@code RefundStatus}
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    public RefundView(
            String refundNo,
            long orderId,
            String orderNo,
            BigDecimal amount,
            int status,
            LocalDateTime refundedAt) {
        this.refundNo = refundNo;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.amount = amount;
        this.status = status;
        this.refundedAt = refundedAt;
    }

    /**
     * Returns the refund number.
     *
     * @return refund number
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefundNo() {
        return refundNo;
    }

    /**
     * Returns the order id.
     *
     * @return order id
     * @author Caspian
     * @since 0.1.0
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Returns the business order number.
     *
     * @return order number
     * @author Caspian
     * @since 0.1.0
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Returns the refunded amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the refund status.
     *
     * @return status from {@code RefundStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns when the refund succeeded.
     *
     * @return refund time, {@code null} while not successful
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
