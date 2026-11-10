package com.lp.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lp.auth.client.AccountDeletionBlockerClient;
import com.lp.auth.client.AccountDeletionCacheClient;
import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.constant.AccountCancellationStatus;
import com.lp.auth.constant.UserStatus;
import com.lp.auth.dto.AccountCancellationView;
import com.lp.auth.dto.AccountDeletionBlockerView;
import com.lp.auth.dto.AccountDeletionStateView;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.entity.UserAccountCancellation;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.mapper.UserAccountCancellationMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Covers the apply, withdraw and query branches without a database or a live trade service.
 *
 * <p>The clock is fixed so the promised deadline and the remaining seconds are exact values rather
 * than approximations.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class AccountCancellationServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0, 0);

    @Mock private AuthUserMapper authUserMapper;

    @Mock private UserAccountCancellationMapper cancellationMapper;

    @Mock private AccountDeletionBlockerClient blockerClient;

    @Mock private AccountDeletionCacheClient cacheClient;

    private AccountDeletionProperties properties;

    private SimpleMeterRegistry meterRegistry;

    private AccountCancellationServiceImpl service;

    private Logger serviceLogger;

    private ListAppender<ILoggingEvent> appender;

    /**
     * Builds the service under test with a fixed clock, in-memory metrics and a log appender that
     * captures the structured apply and withdraw lines.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void setUp() {
        properties = new AccountDeletionProperties();
        meterRegistry = new SimpleMeterRegistry();
        service = serviceAt(NOW);
        serviceLogger = (Logger) LoggerFactory.getLogger(AccountCancellationServiceImpl.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    /** Detaches the appender so the other tests of this module keep their own log state. */
    @AfterEach
    void detachLogAppender() {
        serviceLogger.detachAppender(appender);
    }

    /**
     * Builds the service with the clock fixed at one instant.
     *
     * @param instant instant the service treats as now
     * @return service under test
     * @author Caspian
     * @since 0.3.0
     */
    private AccountCancellationServiceImpl serviceAt(LocalDateTime instant) {
        return new AccountCancellationServiceImpl(
                authUserMapper,
                cancellationMapper,
                blockerClient,
                cacheClient,
                properties,
                meterRegistry,
                Clock.fixed(instant.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    /**
     * Creates a request, fixes the deadline, switches the account and counts the action.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyCreatesRequestAndSwitchesStatus() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null);
        when(blockerClient.blockers(7L))
                .thenReturn(new AccountDeletionBlockerView(7L, 0, 0, false));

        AccountCancellationView view = service.apply(7L, "user");

        ArgumentCaptor<UserAccountCancellation> inserted =
                ArgumentCaptor.forClass(UserAccountCancellation.class);
        verify(cancellationMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(7L);
        assertThat(inserted.getValue().getStatus())
                .isEqualTo(AccountCancellationStatus.COOLING_OFF);
        assertThat(inserted.getValue().getRequestedAt()).isEqualTo(NOW);
        assertThat(inserted.getValue().getEffectiveAt()).isEqualTo(NOW.plusDays(7));
        assertThat(view.status()).isEqualTo(AccountCancellationStatus.COOLING_OFF);
        assertThat(view.effectiveAt()).isEqualTo("2026-11-17T10:00:00");
        assertThat(view.remainingSeconds()).isEqualTo(Duration.ofDays(7).getSeconds());
        verify(authUserMapper).updateStatusIfCurrent(7L, UserStatus.ACTIVE, UserStatus.COOLING_OFF);
        verify(cacheClient).invalidate(7L);
        assertThat(
                        meterRegistry
                                .get(AccountCancellationServiceImpl.METRIC_APPLIED)
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    /**
     * Replays the deadline of a running request without probing trade or inserting a row.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyReplaysExistingRequest() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.COOLING_OFF));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L))
                .thenReturn(request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(3)));

        AccountCancellationView view = service.apply(7L, "user");

        assertThat(view.status()).isEqualTo(AccountCancellationStatus.COOLING_OFF);
        assertThat(view.effectiveAt()).isEqualTo("2026-11-13T10:00:00");
        assertThat(view.remainingSeconds()).isEqualTo(Duration.ofDays(3).getSeconds());
        verifyNoInteractions(blockerClient);
        verify(cancellationMapper, never()).insert(any(UserAccountCancellation.class));
        verify(authUserMapper, never())
                .updateStatusIfCurrent(
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
        // A retry may follow a failed invalidation, so the replay re-sends it.
        verify(cacheClient).invalidate(7L);
        assertThat(meterRegistry.find(AccountCancellationServiceImpl.METRIC_APPLIED).counter())
                .isNull();
    }

    /**
     * Refuses a teacher, operations or admin account because staff records must stay attributable.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyRejectsStaffAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT", "TEACHER"));

        assertThatThrownBy(() -> service.apply(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verifyNoInteractions(blockerClient);
        verify(cancellationMapper, never()).insert(any(UserAccountCancellation.class));
    }

    /**
     * Refuses a request blocked by live orders or refunds and writes nothing.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyRejectsBlockedAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null);
        when(blockerClient.blockers(7L)).thenReturn(new AccountDeletionBlockerView(7L, 2, 1, true));

        assertThatThrownBy(() -> service.apply(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(
                                                ErrorCode.ACCOUNT_CANCELLATION_BLOCKED.getCode()))
                .hasMessageContaining("pending orders=2")
                .hasMessageContaining("refunding requests=1");
        verify(cancellationMapper, never()).insert(any(UserAccountCancellation.class));
        verify(authUserMapper, never())
                .updateStatusIfCurrent(
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * Fails closed with {@code 50401} when the blocker probe is unavailable and writes nothing.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyFailsClosedWhenTradeIsUnavailable() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null);
        when(blockerClient.blockers(7L))
                .thenThrow(new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE));

        assertThatThrownBy(() -> service.apply(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode()));
        verify(cancellationMapper, never()).insert(any(UserAccountCancellation.class));
    }

    /**
     * Refuses a cancelled account with the dedicated account code.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void applyRejectsCancelledAccount() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.CANCELLED));

        assertThatThrownBy(() -> service.apply(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_CANCELLED.getCode()));
        verifyNoInteractions(blockerClient);
    }

    /**
     * Withdraws the active request, restores the account and counts the action.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void withdrawRestoresStatus() {
        UserAccountCancellation active =
                request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(7));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(active);
        when(cancellationMapper.withdraw(11L, NOW)).thenReturn(1);

        service.withdraw(7L, "user");

        verify(cancellationMapper).withdraw(11L, NOW);
        verify(authUserMapper).updateStatusIfCurrent(7L, UserStatus.COOLING_OFF, UserStatus.ACTIVE);
        verify(cacheClient).invalidate(7L);
        assertThat(
                        meterRegistry
                                .get(AccountCancellationServiceImpl.METRIC_WITHDRAWN)
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    /**
     * Answers {@code 67004} and changes nothing when there is no active request.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void withdrawWithoutRequestReturnsNotFound() {
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.withdraw(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(
                                                ErrorCode.ACCOUNT_CANCELLATION_NOT_FOUND
                                                        .getCode()));
        verify(cancellationMapper, never()).withdraw(anyLong(), any(LocalDateTime.class));
        verify(authUserMapper, never())
                .updateStatusIfCurrent(
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * Answers {@code 67004} when a concurrent withdrawal already won the conditional update.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void withdrawLosingRaceReturnsNotFound() {
        UserAccountCancellation active =
                request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(7));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(active);
        when(cancellationMapper.withdraw(11L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.withdraw(7L, "user"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.getCode())
                                        .isEqualTo(
                                                ErrorCode.ACCOUNT_CANCELLATION_NOT_FOUND
                                                        .getCode()));
        verify(authUserMapper, never())
                .updateStatusIfCurrent(
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * AC-6: a withdrawn request is closed and the next application writes a brand new row.
     *
     * <p>The withdraw answer applies the same transition as the SQL ({@code status = 1}), and the
     * active lookup only ever sees {@code status = 0}. The second application runs on a clock one
     * day later, so reusing the withdrawn row would replay the old deadline instead of a new one.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void applyAfterWithdrawalCreatesANewRequestWithANewDeadline() {
        UserAccountCancellation withdrawn =
                request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(7));
        ArgumentCaptor<UserAccountCancellation> inserts =
                ArgumentCaptor.forClass(UserAccountCancellation.class);
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null, withdrawn, null);
        when(cancellationMapper.insert(any(UserAccountCancellation.class))).thenReturn(1);
        when(cancellationMapper.withdraw(11L, NOW))
                .thenAnswer(
                        invocation -> {
                            withdrawn.setStatus(AccountCancellationStatus.WITHDRAWN);
                            return 1;
                        });
        when(blockerClient.blockers(7L))
                .thenReturn(new AccountDeletionBlockerView(7L, 0, 0, false));

        service.apply(7L, "user");
        service.withdraw(7L, "user");
        AccountCancellationView second = serviceAt(NOW.plusDays(1)).apply(7L, "user");

        verify(cancellationMapper, times(2)).insert(inserts.capture());
        UserAccountCancellation renewed = inserts.getAllValues().get(1);
        assertThat(withdrawn.getStatus()).isEqualTo(AccountCancellationStatus.WITHDRAWN);
        assertThat(renewed).isNotSameAs(withdrawn);
        assertThat(renewed.getId()).isNull();
        assertThat(renewed.getStatus()).isEqualTo(AccountCancellationStatus.COOLING_OFF);
        assertThat(renewed.getRequestedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(renewed.getEffectiveAt()).isEqualTo(NOW.plusDays(8));
        assertThat(second.effectiveAt()).isEqualTo("2026-11-18T10:00:00");
    }

    /**
     * AC-25: applying and withdrawing each leave one structured line with the user id, the promised
     * deadline and the trigger source.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void applyAndWithdrawLogTheDeadlineAndSource() {
        UserAccountCancellation active =
                request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(7));
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null, active);
        when(cancellationMapper.insert(any(UserAccountCancellation.class))).thenReturn(1);
        when(cancellationMapper.withdraw(11L, NOW)).thenReturn(1);
        when(blockerClient.blockers(7L))
                .thenReturn(new AccountDeletionBlockerView(7L, 0, 0, false));

        service.apply(7L, "user");
        service.withdraw(7L, "user");

        List<String> lines =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines)
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .contains(
                                                "account cancellation applied",
                                                "userId=7",
                                                "effectiveAt=" + NOW.plusDays(7),
                                                "source=user"))
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .contains(
                                                "account cancellation withdrawn",
                                                "userId=7",
                                                "effectiveAt=" + NOW.plusDays(7),
                                                "source=user"));
    }

    /**
     * Returns the last request with a positive countdown and zero otherwise.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void getReturnsLatestRequest() {
        when(cancellationMapper.selectLatestByUserId(7L))
                .thenReturn(request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusHours(2)));

        AccountCancellationView view = service.get(7L);

        assertThat(view.status()).isEqualTo(AccountCancellationStatus.COOLING_OFF);
        assertThat(view.effectiveAt()).isEqualTo("2026-11-10T12:00:00");
        assertThat(view.remainingSeconds()).isEqualTo(Duration.ofHours(2).getSeconds());

        when(cancellationMapper.selectLatestByUserId(8L))
                .thenReturn(request(12L, AccountCancellationStatus.WITHDRAWN, NOW.plusDays(5)));
        AccountCancellationView withdrawn = service.get(8L);
        assertThat(withdrawn.status()).isEqualTo(AccountCancellationStatus.WITHDRAWN);
        assertThat(withdrawn.remainingSeconds()).isZero();
    }

    /**
     * Returns empty values for a user who never applied.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void getWithoutRequestReturnsEmptyValues() {
        when(cancellationMapper.selectLatestByUserId(7L)).thenReturn(null);

        AccountCancellationView view = service.get(7L);

        assertThat(view.status()).isNull();
        assertThat(view.effectiveAt()).isNull();
        assertThat(view.remainingSeconds()).isZero();
    }

    /**
     * Reports cooling off while the request is active so trade can refuse checkout.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void stateReportsCoolingOffForActiveRequest() {
        when(cancellationMapper.selectLatestByUserId(7L))
                .thenReturn(request(11L, AccountCancellationStatus.COOLING_OFF, NOW.plusDays(7)));

        AccountDeletionStateView state = service.state(7L);

        assertThat(state.userId()).isEqualTo(7L);
        assertThat(state.coolingOff()).isTrue();
        assertThat(state.effectiveAt()).isEqualTo("2026-11-17T10:00:00");
    }

    /**
     * A user whose latest request is withdrawn or absent buys normally.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void stateReportsNoCoolingOffAfterWithdrawalOrWithoutRequest() {
        when(cancellationMapper.selectLatestByUserId(7L))
                .thenReturn(request(11L, AccountCancellationStatus.WITHDRAWN, NOW.plusDays(7)));
        when(cancellationMapper.selectLatestByUserId(8L)).thenReturn(null);

        AccountDeletionStateView withdrawn = service.state(7L);
        AccountDeletionStateView absent = service.state(8L);

        assertThat(withdrawn.coolingOff()).isFalse();
        assertThat(withdrawn.effectiveAt()).isNull();
        assertThat(absent.coolingOff()).isFalse();
        assertThat(absent.effectiveAt()).isNull();
    }

    /**
     * Invalidates trade only after the transaction commits, so a rollback that keeps the database
     * unchanged cannot leave trade with a cleared cache.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void invalidatesTradeCacheOnlyAfterCommit() {
        when(authUserMapper.selectById(7L)).thenReturn(account(UserStatus.ACTIVE));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("STUDENT"));
        when(cancellationMapper.selectActiveByUserId(7L)).thenReturn(null);
        when(blockerClient.blockers(7L))
                .thenReturn(new AccountDeletionBlockerView(7L, 0, 0, false));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.apply(7L, "user");

            verify(cacheClient, never()).invalidate(anyLong());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(cacheClient).invalidate(7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Builds an account row.
     *
     * @param status account status
     * @return account row
     * @author Caspian
     * @since 0.2.0
     */
    private static UserAccount account(int status) {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setStatus(status);
        return account;
    }

    /**
     * Builds a cancellation request row.
     *
     * @param id request id
     * @param status request status
     * @param effectiveAt fixed deadline
     * @return request row
     * @author Caspian
     * @since 0.2.0
     */
    private static UserAccountCancellation request(long id, int status, LocalDateTime effectiveAt) {
        UserAccountCancellation request = new UserAccountCancellation();
        request.setId(id);
        request.setUserId(7L);
        request.setStatus(status);
        request.setRequestedAt(NOW);
        request.setEffectiveAt(effectiveAt);
        return request;
    }
}
