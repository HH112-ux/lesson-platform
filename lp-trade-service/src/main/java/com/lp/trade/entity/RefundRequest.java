package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row read from and written to {@code refund_request}.
 *
 * <p>The unique key {@code uk_order} allows at most one refund per order; {@code refund_no} doubles
 * as the channel idempotency key, so retrying a crashed refund cannot move money twice.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefundRequest {

    private Long id;

    private String refundNo;

    private Long orderId;

    private Long paymentId;

    private Long userId;

    private Integer channel;

    private BigDecimal amount;

    private String reason;

    private Integer status;

    private String channelRefundNo;

    private LocalDateTime refundedAt;

    /**
     * Returns the primary key.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the primary key.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the refund number.
     *
     * @return refund number, also the channel idempotency key
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefundNo() {
        return refundNo;
    }

    /**
     * Sets the refund number.
     *
     * @param refundNo refund number
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    /**
     * Returns the refunded order id.
     *
     * @return order id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the refunded order id.
     *
     * @param orderId order id
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * Returns the successful payment id.
     *
     * @return payment id, {@code null} when the order was free
     * @author Caspian
     * @since 0.1.0
     */
    public Long getPaymentId() {
        return paymentId;
    }

    /**
     * Sets the successful payment id.
     *
     * @param paymentId payment id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    /**
     * Returns the buyer id.
     *
     * @return buyer user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the buyer id.
     *
     * @param userId buyer user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the payment channel.
     *
     * @return channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the payment channel.
     *
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /**
     * Returns the refund amount.
     *
     * @return amount in yuan, never above the paid amount
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the refund amount.
     *
     * @param amount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns the buyer supplied reason.
     *
     * @return reason text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the buyer supplied reason.
     *
     * @param reason reason text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Returns the refund status.
     *
     * @return status from {@link com.lp.trade.domain.RefundStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the refund status.
     *
     * @param status status from {@link com.lp.trade.domain.RefundStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the channel refund number.
     *
     * @return channel refund number, {@code null} before success
     * @author Caspian
     * @since 0.1.0
     */
    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    /**
     * Sets the channel refund number.
     *
     * @param channelRefundNo channel refund number
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannelRefundNo(String channelRefundNo) {
        this.channelRefundNo = channelRefundNo;
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

    /**
     * Sets when the refund succeeded.
     *
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }
}
