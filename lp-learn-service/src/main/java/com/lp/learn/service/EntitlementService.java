package com.lp.learn.service;

/**
 * Grants and checks course access.
 *
 * <p>Grants are idempotent: a repeated delivery of the same {@code userId + courseId} is accepted
 * and leaves the first row untouched.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface EntitlementService {

    /**
     * Grants one course to one student; safe to call repeatedly.
     *
     * @param userId student user id
     * @param courseId course id
     * @param orderId order id, {@code null} for operations-issued access
     * @param source 1 purchase, 2 operations grant, 3 redemption
     * @throws com.lp.common.exception.BizException when a required field is missing
     * @author Caspian
     * @since 0.1.0
     */
    void grant(long userId, long courseId, Long orderId, int source);

    /**
     * Tells whether the student currently owns the course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return {@code true} when an active entitlement exists
     * @author Caspian
     * @since 0.1.0
     */
    boolean hasEntitlement(long userId, long courseId);
}
