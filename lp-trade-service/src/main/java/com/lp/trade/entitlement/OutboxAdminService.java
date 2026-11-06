package com.lp.trade.entitlement;

import com.lp.trade.dto.FailedOutboxPage;

/**
 * Operations view of entitlement intents that the relay gave up on.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface OutboxAdminService {

    /**
     * Lists given up intents, newest first.
     *
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return one page of given up intents
     * @author Caspian
     * @since 0.1.0
     */
    FailedOutboxPage failed(int pageNum, int pageSize);

    /**
     * Puts one given up intent back into the pending queue.
     *
     * @param id outbox id
     * @return {@code true} when the row was given up and is now pending again
     * @author Caspian
     * @since 0.1.0
     */
    boolean retry(long id);
}
