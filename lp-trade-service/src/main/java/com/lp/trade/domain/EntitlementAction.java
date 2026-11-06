package com.lp.trade.domain;

/**
 * Actions stored in {@code entitlement_outbox.action}.
 *
 * <p>Refund revocation reuses the grant delivery machinery: retries, exponential backoff, the dead
 * letter queue and the operations replay endpoint all work unchanged.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class EntitlementAction {

    /** Grant course access to the buyer. */
    public static final int GRANT = 0;

    /** Revoke course access after a refund. */
    public static final int REVOKE = 1;

    private EntitlementAction() {}
}
