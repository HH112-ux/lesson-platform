package com.lp.learn.domain;

/**
 * Entitlement lifecycle values stored in {@code user_entitlement.status}.
 *
 * <p>A refund revokes access without deleting the row: the learning progress stays readable and a
 * later purchase of the same course reactivates that row instead of inserting a second one.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class EntitlementStatus {

    /** The student may access the course. */
    public static final int ACTIVE = 0;

    /** Access was revoked by a refund; the row and its learning records are kept. */
    public static final int REVOKED = 1;

    private EntitlementStatus() {}
}
