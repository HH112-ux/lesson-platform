package com.lp.trade.entity;

import java.time.LocalDateTime;

/**
 * Row read from {@code user_coupon}.
 *
 * <p>Status values are 0 unused, 1 used and 2 expired. A coupon is consumed exactly once by a
 * conditional update on {@code status = 0}, which also makes concurrent orders safe.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class UserCoupon {

    private Long id;

    private Long userId;

    private Long couponId;

    private Integer status;

    private Long orderId;

    private LocalDateTime usedAt;

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
     * Returns the owner user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the owner user id.
     *
     * @param userId user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the coupon template id.
     *
     * @return coupon id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCouponId() {
        return couponId;
    }

    /**
     * Sets the coupon template id.
     *
     * @param couponId coupon id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    /**
     * Returns the usage status.
     *
     * @return 0 unused, 1 used, 2 expired
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the usage status.
     *
     * @param status 0 unused, 1 used, 2 expired
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the order that consumed this coupon.
     *
     * @return order id, {@code null} while unused
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the order that consumed this coupon.
     *
     * @param orderId order id
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * Returns the usage time.
     *
     * @return usage time, {@code null} while unused
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    /**
     * Sets the usage time.
     *
     * @param usedAt usage time
     * @author Caspian
     * @since 0.1.0
     */
    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
