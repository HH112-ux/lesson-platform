package com.lp.trade.entity;

import java.time.LocalDateTime;

/**
 * Row read from and written to {@code entitlement_outbox}.
 *
 * <p>Written in the same transaction as the order or payment state change, so a committed payment
 * can never lose its entitlement intent. The relay delivers it to learn-service later.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class EntitlementOutbox {

    private Long id;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long courseId;

    private Integer action;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime deliveredAt;

    private String lastError;

    private LocalDateTime updatedAt;

    /**
     * Returns the last update time.
     *
     * @return update time, {@code null} when the projection does not select it
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update time.
     *
     * @param updatedAt update time
     * @author Caspian
     * @since 0.1.0
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

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
     * Returns the order id.
     *
     * @return order id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the order id.
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
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the delivery action.
     *
     * @return 0 grant, 1 revoke
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getAction() {
        return action;
    }

    /**
     * Sets the delivery action.
     *
     * @param action 0 grant, 1 revoke
     * @author Caspian
     * @since 0.1.0
     */
    public void setAction(Integer action) {
        this.action = action;
    }

    /**
     * Returns the delivery status.
     *
     * @return 0 pending, 1 delivered, 2 given up
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the delivery status.
     *
     * @param status 0 pending, 1 delivered, 2 given up
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns how many delivery attempts failed.
     *
     * @return retry count
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * Sets how many delivery attempts failed.
     *
     * @param retryCount retry count
     * @author Caspian
     * @since 0.1.0
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * Returns when the next delivery attempt may run.
     *
     * @return next retry time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * Sets when the next delivery attempt may run.
     *
     * @param nextRetryAt next retry time
     * @author Caspian
     * @since 0.1.0
     */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * Returns when delivery succeeded.
     *
     * @return delivery time, {@code null} while pending
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    /**
     * Sets when delivery succeeded.
     *
     * @param deliveredAt delivery time, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    /**
     * Returns the last failure reason.
     *
     * @return error text, {@code null} before the first failure
     * @author Caspian
     * @since 0.1.0
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the last failure reason.
     *
     * @param lastError error text, must not contain secrets
     * @author Caspian
     * @since 0.1.0
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
