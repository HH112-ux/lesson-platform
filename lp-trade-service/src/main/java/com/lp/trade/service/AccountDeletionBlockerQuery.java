package com.lp.trade.service;

import com.lp.trade.dto.AccountDeletionBlockerView;
import com.lp.trade.mapper.AccountDeletionBlockerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers whether an account still has trade rows that must outlive its deletion.
 *
 * <p>Read only and cheap: both counts are covered by the user id prefix of the existing indexes.
 * The query runs in a read-only transaction so a caller that later writes cannot accidentally reuse
 * the snapshot.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class AccountDeletionBlockerQuery {

    private final AccountDeletionBlockerMapper blockerMapper;

    /**
     * Creates the query with its mapper.
     *
     * @param blockerMapper blocker counts
     * @author Caspian
     * @since 0.2.0
     */
    public AccountDeletionBlockerQuery(AccountDeletionBlockerMapper blockerMapper) {
        this.blockerMapper = blockerMapper;
    }

    /**
     * Counts the blocking rows of a user.
     *
     * @param userId account owner
     * @return blocker counts plus the aggregated decision
     * @author Caspian
     * @since 0.2.0
     */
    @Transactional(readOnly = true)
    public AccountDeletionBlockerView blockers(long userId) {
        int pendingOrders = blockerMapper.countBlockingOrders(userId);
        int refundingRequests = blockerMapper.countBlockingRefunds(userId);
        return new AccountDeletionBlockerView(
                userId,
                pendingOrders,
                refundingRequests,
                pendingOrders > 0 || refundingRequests > 0);
    }
}
