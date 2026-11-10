package com.lp.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.AccountDeletionStateClient;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.AccountDeletionStateView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the cooling-off gate, its per-user cache and its fail-closed behaviour.
 *
 * <p>The auth-service probe is mocked: the value of this class is deciding what to do with a
 * cached, fresh or missing answer, not the transport.
 *
 * @author Caspian
 * @since 0.3.0
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionGuardTest {

    private static final long USER_ID = 1001L;

    @Mock private AccountDeletionStateClient stateClient;

    private TradeProperties properties;

    private SimpleMeterRegistry meterRegistry;

    private AccountDeletionGuard guard;

    /**
     * Builds the guard with the production TTL and in-memory metrics.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void setUp() {
        properties = new TradeProperties();
        meterRegistry = new SimpleMeterRegistry();
        guard = new AccountDeletionGuard(stateClient, properties, meterRegistry);
    }

    /**
     * Refuses checkout with {@code 67005} while auth reports an active cooling-off period.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsBuyerInCoolingOffPeriod() {
        when(stateClient.state(USER_ID))
                .thenReturn(new AccountDeletionStateView(USER_ID, true, "2026-11-17T10:00:00"));

        assertThatThrownBy(() -> guard.assertNotCoolingOff(USER_ID))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(
                                                ErrorCode.ACCOUNT_COOLING_OFF_RESTRICTED
                                                        .getCode()));
    }

    /**
     * A successful answer is reused, so a buyer costs one internal call per TTL.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void cachesSuccessfulAnswerPerUser() {
        when(stateClient.state(USER_ID))
                .thenReturn(new AccountDeletionStateView(USER_ID, false, null));

        guard.assertNotCoolingOff(USER_ID);
        guard.assertNotCoolingOff(USER_ID);

        verify(stateClient, times(1)).state(USER_ID);
    }

    /**
     * A failed probe is never remembered, so recovery does not wait for the TTL.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void doesNotCacheFailedProbe() {
        when(stateClient.state(USER_ID))
                .thenThrow(new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> guard.assertNotCoolingOff(USER_ID))
                    .isInstanceOfSatisfying(
                            BizException.class,
                            exception ->
                                    assertThat(exception.getCode())
                                            .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode()));
        }

        verify(stateClient, times(2)).state(USER_ID);
        assertThat(meterRegistry.get(AccountDeletionGuard.METRIC_DEGRADED).counter().count())
                .isEqualTo(2);
    }

    /**
     * The cached answer expires after the configured TTL.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void reprobesAfterTtlExpires() {
        properties.setAccountGuardCacheTtl(Duration.ZERO);
        when(stateClient.state(USER_ID))
                .thenReturn(new AccountDeletionStateView(USER_ID, false, null));

        guard.assertNotCoolingOff(USER_ID);
        guard.assertNotCoolingOff(USER_ID);

        verify(stateClient, times(2)).state(USER_ID);
    }

    /**
     * An active invalidation from auth drops the cached answer immediately.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void evictForcesTheNextRequestToProbeAgain() {
        when(stateClient.state(USER_ID))
                .thenReturn(new AccountDeletionStateView(USER_ID, false, null));

        guard.assertNotCoolingOff(USER_ID);
        guard.evict(USER_ID);
        guard.assertNotCoolingOff(USER_ID);

        verify(stateClient, times(2)).state(USER_ID);
    }
}
