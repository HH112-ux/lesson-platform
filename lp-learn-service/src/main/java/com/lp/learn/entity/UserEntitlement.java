package com.lp.learn.entity;

import java.time.LocalDateTime;

/**
 * Row read from and written to {@code user_entitlement}.
 *
 * <p>A row means the student may access the course. The pair {@code userId + courseId} is unique,
 * which is what makes repeated grants from the trade outbox harmless.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class UserEntitlement {

    private Long id;

    private Long userId;

    private Long courseId;

    private Long orderId;

    private Integer source;

    private Integer status;

    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    private LocalDateTime expireAt;

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
     * Returns the order that produced this entitlement.
     *
     * @return order id, {@code null} for operations-issued access
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the order that produced this entitlement.
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

    /**
     * Returns the access status.
     *
     * @return status from {@link com.lp.learn.domain.EntitlementStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the access status.
     *
     * @param status status from {@link com.lp.learn.domain.EntitlementStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns when access was granted.
     *
     * @return grant time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    /**
     * Sets when access was granted.
     *
     * @param grantedAt grant time
     * @author Caspian
     * @since 0.1.0
     */
    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    /**
     * Returns when access was revoked by a refund.
     *
     * @return revoke time, {@code null} while the entitlement is active
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    /**
     * Sets when access was revoked by a refund.
     *
     * @param revokedAt revoke time, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Returns when access expires.
     *
     * @return expiry time, {@code null} means permanent
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    /**
     * Sets when access expires.
     *
     * @param expireAt expiry time, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
