package com.lp.trade.dto;

import java.util.List;

/**
 * Request body of {@code POST /api/v1/orders}.
 *
 * <p>The payment channel is never part of the body: it is derived from the trusted {@code
 * X-User-Source} header so a client cannot choose a channel.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CreateOrderRequest {

    private Long courseId;

    private List<Long> courseIds;

    private String idempotencyKey;

    private Long couponId;

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
     * Returns the course ids of a combined order.
     *
     * <p>Exactly one of {@code courseId} and {@code courseIds} must be supplied. The list is
     * deduplicated and sorted by the service, so its order is irrelevant to idempotency.
     *
     * @return course ids, {@code null} for the legacy single course request
     * @author Caspian
     * @since 0.22.0
     */
    public List<Long> getCourseIds() {
        return courseIds;
    }

    /**
     * Sets the course ids of a combined order.
     *
     * @param courseIds course ids, at most 20 distinct values
     * @author Caspian
     * @since 0.22.0
     */
    public void setCourseIds(List<Long> courseIds) {
        this.courseIds = courseIds;
    }

    /**
     * Returns the client idempotency key.
     *
     * @return idempotency key
     * @author Caspian
     * @since 0.1.0
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Sets the client idempotency key.
     *
     * @param idempotencyKey idempotency key, at most 64 characters
     * @author Caspian
     * @since 0.1.0
     */
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Returns the optional coupon id.
     *
     * @return coupon id, {@code null} when no coupon is used
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCouponId() {
        return couponId;
    }

    /**
     * Sets the optional coupon id.
     *
     * @param couponId coupon id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }
}
