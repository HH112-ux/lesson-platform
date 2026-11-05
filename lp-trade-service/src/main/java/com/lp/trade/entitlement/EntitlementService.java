package com.lp.trade.entitlement;

/**
 * Records the intent to grant course ownership after an order completes.
 *
 * <p>This is a port so the trade service never calls learn-service over HTTP inside a transaction.
 * The implementation writes a local outbox row in the same transaction as the order/payment state
 * change; a scheduled relay delivers it to learn-service. See {@code entitlement_outbox} and {@code
 * docs/engineering-plan.md} section 5.6.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface EntitlementService {

    /**
     * Records that the buyer must receive access to one course.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @param orderId order id
     * @param orderNo order number, kept for support and audit
     * @author Caspian
     * @since 0.1.0
     */
    void grant(long userId, long courseId, long orderId, String orderNo);
}
