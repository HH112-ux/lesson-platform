package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund row joined with its order number for the operations review list.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class AdminRefundRow {

    private Long id;

    private String refundNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Integer channel;

    private BigDecimal amount;

    private String reason;

    private String reviewReason;

    private Integer status;

    private String failReason;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    /**
     * Returns the refund primary key.
     *
     * @return refund id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the refund primary key.
     *
     * @param id refund id
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
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
     * Returns the order primary key.
     *
     * @return order id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the order primary key.
     *
     * @param orderId order id
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * Returns the order number.
     *
     * @return order number
     * @author Caspian
     * @since 0.1.0
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Sets the order number.
     *
     * @param orderNo order number
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * Returns the buyer user id.
     *
     * @return buyer id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the buyer user id.
     *
     * @param userId buyer id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the payment channel.
     *
     * @return channel value from the payment record
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the payment channel.
     *
     * @param channel channel value
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /**
     * Returns the refund amount.
     *
     * @return amount in yuan
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
     * @return application reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the buyer supplied reason.
     *
     * @param reason application reason
     * @author Caspian
     * @since 0.1.0
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Returns the operations review comment.
     *
     * @return review comment or rejection reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getReviewReason() {
        return reviewReason;
    }

    /**
     * Sets the operations review comment.
     *
     * @param reviewReason review comment or rejection reason
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
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
     * @param status refund status
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the channel failure reason.
     *
     * @return failure reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getFailReason() {
        return failReason;
    }

    /**
     * Sets the channel failure reason.
     *
     * @param failReason failure reason
     * @author Caspian
     * @since 0.1.0
     */
    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    /**
     * Returns the reviewer user id.
     *
     * @return reviewer id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getReviewedBy() {
        return reviewedBy;
    }

    /**
     * Sets the reviewer user id.
     *
     * @param reviewedBy reviewer id
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    /**
     * Returns the review time.
     *
     * @return review time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    /**
     * Sets the review time.
     *
     * @param reviewedAt review time
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    /**
     * Returns the refund completion time.
     *
     * @return refund time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    /**
     * Sets the refund completion time.
     *
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    /**
     * Returns when the buyer applied.
     *
     * @return application time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets when the buyer applied.
     *
     * @param createdAt application time
     * @author Caspian
     * @since 0.1.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
