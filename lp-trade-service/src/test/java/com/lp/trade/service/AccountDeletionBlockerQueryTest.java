package com.lp.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lp.trade.dto.AccountDeletionBlockerView;
import com.lp.trade.mapper.AccountDeletionBlockerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the blocker decision handed to auth-service.
 *
 * <p>The mapper is mocked, so the tests pin the aggregation rule only: any blocking order or refund
 * blocks the cancellation, and the reported counts are passed through untouched.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionBlockerQueryTest {

    private static final long USER_ID = 2002L;

    @Mock private AccountDeletionBlockerMapper blockerMapper;

    private AccountDeletionBlockerQuery query;

    /**
     * Wires the query with its mocked mapper.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void setUp() {
        query = new AccountDeletionBlockerQuery(blockerMapper);
    }

    /**
     * An account without unpaid orders or live refunds is not blocked.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void reportsUnblockedWhenBothCountsAreZero() {
        when(blockerMapper.countBlockingOrders(USER_ID)).thenReturn(0);
        when(blockerMapper.countBlockingRefunds(USER_ID)).thenReturn(0);

        AccountDeletionBlockerView view = query.blockers(USER_ID);

        assertThat(view.blocked()).isFalse();
        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.pendingOrders()).isZero();
        assertThat(view.refundingRequests()).isZero();
    }

    /**
     * An unpaid order alone blocks the cancellation.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void reportsBlockedByPendingOrder() {
        when(blockerMapper.countBlockingOrders(USER_ID)).thenReturn(2);
        when(blockerMapper.countBlockingRefunds(USER_ID)).thenReturn(0);

        AccountDeletionBlockerView view = query.blockers(USER_ID);

        assertThat(view.blocked()).isTrue();
        assertThat(view.pendingOrders()).isEqualTo(2);
        assertThat(view.refundingRequests()).isZero();
    }

    /**
     * A refund that is not finished yet blocks the cancellation on its own.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void reportsBlockedByLiveRefund() {
        when(blockerMapper.countBlockingOrders(USER_ID)).thenReturn(0);
        when(blockerMapper.countBlockingRefunds(USER_ID)).thenReturn(1);

        AccountDeletionBlockerView view = query.blockers(USER_ID);

        assertThat(view.blocked()).isTrue();
        assertThat(view.pendingOrders()).isZero();
        assertThat(view.refundingRequests()).isEqualTo(1);
    }
}
