package com.lp.learn.dto;

/**
 * Revokes one entitlement after its order was refunded.
 *
 * <p>Sent by the trade outbox relay, so it may be delivered more than once and in either order with
 * the original grant.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RevokeEntitlementRequest {

    private Long userId;

    private Long courseId;

    private Long orderId;

    /**
     * Returns the student id.
     *
     * @return student user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the student id.
     *
     * @param userId student user id
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
     * Returns the refunded order id.
     *
     * @return order id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the refunded order id.
     *
     * @param orderId order id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * Tells whether the request carries the fields the endpoint requires.
     *
     * @return {@code true} when user and course are both present
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isComplete() {
        return userId != null && courseId != null;
    }
}
