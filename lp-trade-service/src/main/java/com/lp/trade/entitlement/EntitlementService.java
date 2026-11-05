package com.lp.trade.entitlement;

/**
 * Grants course ownership after an order completes.
 *
 * <p>This is a port so the trade service never calls learn-service over HTTP during checkout. The
 * current placeholder implementation only records the intent; the real write into {@code lp_lms}
 * arrives with the learn-service integration and must be treated as the single source of truth.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface EntitlementService {

    /**
     * Grants the buyer access to one course.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @param orderNo order that completed payment
     * @author Caspian
     * @since 0.1.0
     */
    void grant(long userId, long courseId, String orderNo);
}
