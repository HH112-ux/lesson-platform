package com.lp.trade.dto;

import java.time.LocalDateTime;

/**
 * One given up entitlement intent, as shown to operations.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class FailedOutboxView {

    private final long id;

    private final long orderId;

    private final long courseId;

    private final long userId;

    private final int retryCount;

    private final String lastError;

    private final LocalDateTime updatedAt;

    /**
     * Creates one projection.
     *
     * @param id outbox id
     * @param orderId order id
     * @param courseId course id
     * @param userId buyer user id
     * @param retryCount failed attempt count
     * @param lastError last failure reason
     * @param updatedAt last update time
     * @author Caspian
     * @since 0.1.0
     */
    public FailedOutboxView(
            long id,
            long orderId,
            long courseId,
            long userId,
            int retryCount,
            String lastError,
            LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.courseId = courseId;
        this.userId = userId;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the outbox id.
     *
     * @return outbox id
     * @author Caspian
     * @since 0.1.0
     */
    public long getId() {
        return id;
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
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Returns the buyer user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public long getUserId() {
        return userId;
    }

    /**
     * Returns the failed attempt count.
     *
     * @return retry count
     * @author Caspian
     * @since 0.1.0
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Returns the last failure reason.
     *
     * @return error text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Returns the last update time.
     *
     * @return update time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
