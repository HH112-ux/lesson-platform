package com.lp.learn.dto;

/**
 * Internal request that grants one course to one student.
 *
 * <p>Sent by the trade outbox relay. Delivery is at-least-once, so {@code userId + courseId} is the
 * idempotency key and a repeated delivery is accepted as success.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class GrantEntitlementRequest {

    private Long userId;

    private Long courseId;

    private Long orderId;

    private Integer source;

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
     * Returns the order that produced the grant.
     *
     * @return order id, {@code null} for operations-issued access
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the order that produced the grant.
     *
     * @param orderId order id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * Returns the grant source.
     *
     * @return 1 purchase, 2 operations grant, 3 redemption
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getSource() {
        return source;
    }

    /**
     * Sets the grant source.
     *
     * @param source 1 purchase, 2 operations grant, 3 redemption
     * @author Caspian
     * @since 0.1.0
     */
    public void setSource(Integer source) {
        this.source = source;
    }
}
