package com.lp.trade.purchase;

/**
 * Payload bound to a purchase ticket.
 *
 * <p>The ticket itself is an opaque random string; only this payload is stored server side, keyed
 * by the SHA-256 of the ticket. No user id ever appears in the URL or in logs.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PurchaseTicketPayload {

    private long userId;

    private long courseId;

    private long expiresAt;

    /**
     * Creates an empty payload for JSON deserialization.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public PurchaseTicketPayload() {}

    /**
     * Creates a payload.
     *
     * @param userId owner user id
     * @param courseId course to purchase
     * @param expiresAt expiry epoch millisecond
     * @author Caspian
     * @since 0.1.0
     */
    public PurchaseTicketPayload(long userId, long courseId, long expiresAt) {
        this.userId = userId;
        this.courseId = courseId;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the owner user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public long getUserId() {
        return userId;
    }

    /**
     * Sets the owner user id.
     *
     * @param userId user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(long userId) {
        this.userId = userId;
    }

    /**
     * Returns the course to purchase.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course to purchase.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the expiry epoch millisecond.
     *
     * @return epoch millisecond
     * @author Caspian
     * @since 0.1.0
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expiry epoch millisecond.
     *
     * @param expiresAt epoch millisecond
     * @author Caspian
     * @since 0.1.0
     */
    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
