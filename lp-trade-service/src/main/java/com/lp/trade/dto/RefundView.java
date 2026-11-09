package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund result returned to the buyer or the operations console.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefundView {

    private final long refundId;

    private final String refundNo;

    private final long orderId;

    private final String orderNo;

    private final int channel;

    private final BigDecimal amount;

    private final int status;

    private final String reason;

    private final String reviewReason;

    private final String failReason;

    private final LocalDateTime refundedAt;

    /**
     * Creates the projection.
     *
     * @param refundId refund primary key
     * @param refundNo refund number, also the channel idempotency key
     * @param orderId order id
     * @param orderNo business order number
     * @param channel channel fixed on the successful payment
     * @param amount refunded amount in yuan
     * @param status status from {@code RefundStatus}
     * @param reason buyer supplied reason
     * @param reviewReason operations comment or rejection reason
     * @param failReason channel failure reason
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    public RefundView(
            long refundId,
            String refundNo,
            long orderId,
            String orderNo,
            int channel,
            BigDecimal amount,
            int status,
            String reason,
            String reviewReason,
            String failReason,
            LocalDateTime refundedAt) {
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.channel = channel;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.reviewReason = reviewReason;
        this.failReason = failReason;
        this.refundedAt = refundedAt;
    }

    /**
     * Returns the refund primary key.
     *
     * @return refund id
     * @author Caspian
     * @since 0.1.0
     */
    public long getRefundId() {
        return refundId;
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
     * Returns the payment channel.
     *
     * @return channel fixed on the successful payment
     * @author Caspian
     * @since 0.1.0
     */
    public int getChannel() {
        return channel;
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
     * Returns the buyer supplied reason.
     *
     * @return application reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the operations comment.
     *
     * @return review comment or rejection reason, {@code null} while pending
     * @author Caspian
     * @since 0.1.0
     */
    public String getReviewReason() {
        return reviewReason;
    }

    /**
     * Returns the channel failure reason.
     *
     * @return failure reason, {@code null} unless the status is {@code 4}
     * @author Caspian
     * @since 0.1.0
     */
    public String getFailReason() {
        return failReason;
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
