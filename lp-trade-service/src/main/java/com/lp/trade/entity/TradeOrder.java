package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row read from and written to {@code trade_order}.
 *
 * <p>All amounts are in yuan with two decimals. Status values come from {@link
 * com.lp.trade.domain.OrderStatus}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class TradeOrder {

    private Long id;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private Integer status;

    private Long couponId;

    private Integer channel;

    private LocalDateTime payExpireAt;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    private String idempotencyKey;

    private LocalDateTime createdAt;

    private String cancelReason;

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
     * Sets the business order number.
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
     * @return buyer user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the buyer user id.
     *
     * @param userId buyer user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the original total amount.
     *
     * @return total amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Sets the original total amount.
     *
     * @param totalAmount total amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * Returns the discount amount.
     *
     * @return discount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    /**
     * Sets the discount amount.
     *
     * @param discountAmount discount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    /**
     * Returns the payable amount.
     *
     * @return payable amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    /**
     * Sets the payable amount.
     *
     * @param payAmount payable amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    /**
     * Returns the order status.
     *
     * @return status defined by {@link com.lp.trade.domain.OrderStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the order status.
     *
     * @param status status defined by {@link com.lp.trade.domain.OrderStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the consumed coupon id.
     *
     * @return coupon id, {@code null} when no coupon was used
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCouponId() {
        return couponId;
    }

    /**
     * Sets the consumed coupon id.
     *
     * @param couponId coupon id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    /**
     * Returns the fixed payment channel.
     *
     * @return channel defined by {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the fixed payment channel.
     *
     * @param channel channel defined by {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /**
     * Returns the payment deadline.
     *
     * @return deadline, {@code null} for an already completed order
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPayExpireAt() {
        return payExpireAt;
    }

    /**
     * Sets the payment deadline.
     *
     * @param payExpireAt deadline
     * @author Caspian
     * @since 0.1.0
     */
    public void setPayExpireAt(LocalDateTime payExpireAt) {
        this.payExpireAt = payExpireAt;
    }

    /**
     * Returns the payment time.
     *
     * @return payment time, {@code null} before payment
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    /**
     * Sets the payment time.
     *
     * @param paidAt payment time
     * @author Caspian
     * @since 0.1.0
     */
    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    /**
     * Returns the close time.
     *
     * @return close time, {@code null} until closed
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /**
     * Sets the close time.
     *
     * @param closedAt close time
     * @author Caspian
     * @since 0.1.0
     */
    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    /**
     * Returns the client supplied idempotency key.
     *
     * @return idempotency key
     * @author Caspian
     * @since 0.1.0
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Sets the client supplied idempotency key.
     *
     * @param idempotencyKey idempotency key
     * @author Caspian
     * @since 0.1.0
     */
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Returns the creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.1.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the cancel reason.
     *
     * @return reason text, {@code null} when the order was not cancelled by the buyer
     * @author Caspian
     * @since 0.1.0
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Sets the cancel reason.
     *
     * @param cancelReason reason text
     * @author Caspian
     * @since 0.1.0
     */
    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
