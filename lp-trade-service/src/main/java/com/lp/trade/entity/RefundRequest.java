package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row read from and written to {@code refund_request}.
 *
 * <p>The generated column {@code active_order_id} and its unique key allow at most one in-progress
 * refund per order while leaving rejected or failed requests retryable. {@code refund_no} doubles
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

    private String reviewReason;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private Integer status;

    private String channelRefundNo;

    private Integer reconcileAttempts;

    private LocalDateTime reconcileCheckedAt;

    private String reconcileFinding;

    private String failReason;

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
     * Returns the operations review comment.
     *
     * @return review comment, required when the request was rejected
     * @author Caspian
     * @since 0.1.0
     */
    public String getReviewReason() {
        return reviewReason;
    }

    /**
     * Sets the operations review comment.
     *
     * @param reviewReason review comment, may be {@code null} before a decision
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    /**
     * Returns the operator who made the decision.
     *
     * @return operator user id, {@code null} while pending
     * @author Caspian
     * @since 0.1.0
     */
    public Long getReviewedBy() {
        return reviewedBy;
    }

    /**
     * Sets the operator who made the decision.
     *
     * @param reviewedBy operator user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    /**
     * Returns when the decision was made.
     *
     * @return decision time, {@code null} while pending
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    /**
     * Sets when the decision was made.
     *
     * @param reviewedAt decision time
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
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
     * Returns how many times the read-only reconciliation queried the channel.
     *
     * @return query count, {@code 0} before the first scan
     * @author Caspian
     * @since 0.7.0
     */
    public Integer getReconcileAttempts() {
        return reconcileAttempts;
    }

    /**
     * Sets how many times the read-only reconciliation queried the channel.
     *
     * @param reconcileAttempts query count
     * @author Caspian
     * @since 0.7.0
     */
    public void setReconcileAttempts(Integer reconcileAttempts) {
        this.reconcileAttempts = reconcileAttempts;
    }

    /**
     * Returns when the read-only reconciliation last queried the channel.
     *
     * @return last query time, {@code null} before the first scan
     * @author Caspian
     * @since 0.7.0
     */
    public LocalDateTime getReconcileCheckedAt() {
        return reconcileCheckedAt;
    }

    /**
     * Sets when the read-only reconciliation last queried the channel.
     *
     * @param reconcileCheckedAt last query time
     * @author Caspian
     * @since 0.7.0
     */
    public void setReconcileCheckedAt(LocalDateTime reconcileCheckedAt) {
        this.reconcileCheckedAt = reconcileCheckedAt;
    }

    /**
     * Returns the last reconciliation finding, used to log only when it changes.
     *
     * @return finding code, {@code null} before the first scan
     * @author Caspian
     * @since 0.7.0
     */
    public String getReconcileFinding() {
        return reconcileFinding;
    }

    /**
     * Sets the last reconciliation finding.
     *
     * @param reconcileFinding finding code
     * @author Caspian
     * @since 0.7.0
     */
    public void setReconcileFinding(String reconcileFinding) {
        this.reconcileFinding = reconcileFinding;
    }

    /**
     * Returns why the channel refund failed.
     *
     * @return failure reason, {@code null} unless the status is {@code 4}
     * @author Caspian
     * @since 0.1.0
     */
    public String getFailReason() {
        return failReason;
    }

    /**
     * Sets why the channel refund failed.
     *
     * @param failReason failure reason, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setFailReason(String failReason) {
        this.failReason = failReason;
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
