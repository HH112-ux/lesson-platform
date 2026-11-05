package com.lp.trade.entitlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder {@link EntitlementService} used until learn-service exposes a write API.
 *
 * <p>It deliberately does not persist anything and never reports a granted entitlement as a
 * delivered access right. The log line is the audit trail for this milestone; replace this bean
 * with a learn-service backed implementation before enabling real payments.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class PlaceholderEntitlementService implements EntitlementService {

    private static final Logger LOG = LoggerFactory.getLogger(PlaceholderEntitlementService.class);

    /**
     * Records the entitlement intent without persisting it.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @param orderNo completed order number
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void grant(long userId, long courseId, String orderNo) {
        LOG.info(
                "entitlement grant pending learn-service integration: user={}, course={},"
                        + " order={}",
                userId,
                courseId,
                orderNo);
    }
}
