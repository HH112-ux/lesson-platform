package com.lp.trade.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    private final long amountFen;

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
     * @param amountFen refunded amount in fen
     * @param status status from {@code RefundStatus}
     * @param reason buyer supplied reason
     * @param reviewReason operations comment or rejection reason
     * @param failReason channel failure reason
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    @JsonCreator
    public RefundView(
            @JsonProperty("refundId") long refundId,
            @JsonProperty("refundNo") String refundNo,
            @JsonProperty("orderId") long orderId,
            @JsonProperty("orderNo") String orderNo,
            @JsonProperty("channel") int channel,
            @JsonProperty("amountFen") long amountFen,
            @JsonProperty("status") int status,
            @JsonProperty("reason") String reason,
            @JsonProperty("reviewReason") String reviewReason,
            @JsonProperty("failReason") String failReason,
            @JsonProperty("refundedAt") LocalDateTime refundedAt) {
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.channel = channel;
        this.amountFen = amountFen;
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
     * @return amount in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getAmountFen() {
        return amountFen;
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
