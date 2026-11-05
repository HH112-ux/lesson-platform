package com.lp.trade.entitlement;

import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * {@link EntitlementService} backed by the local outbox table.
 *
 * <p>Must run inside the caller's transaction: the order/payment state and the grant intent then
 * commit or roll back together. Nothing here talks to learn-service; delivery is the relay's job.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class OutboxEntitlementService implements EntitlementService {

    private final EntitlementOutboxMapper entitlementOutboxMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param entitlementOutboxMapper outbox persistence
     * @author Caspian
     * @since 0.1.0
     */
    public OutboxEntitlementService(EntitlementOutboxMapper entitlementOutboxMapper) {
        this.entitlementOutboxMapper = entitlementOutboxMapper;
    }

    /**
     * Records the grant intent; a duplicate order/course pair is ignored.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @param orderId order id
     * @param orderNo order number, kept for support and audit
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void grant(long userId, long courseId, long orderId, String orderNo) {
        EntitlementOutbox outbox = new EntitlementOutbox();
        outbox.setOrderId(orderId);
        outbox.setOrderNo(orderNo);
        outbox.setUserId(userId);
        outbox.setCourseId(courseId);
        try {
            entitlementOutboxMapper.insert(outbox);
        } catch (DuplicateKeyException exception) {
            // uk_order_course already holds this intent; the relay will deliver it once.
        }
    }
}
