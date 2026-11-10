package com.lp.auth.job;

import static com.lp.auth.job.AccountCancellationExecutionJob.METRIC_EXECUTED;
import static com.lp.auth.job.AccountCancellationExecutionJob.METRIC_FAILED;
import static com.lp.auth.job.AccountCancellationExecutionJob.METRIC_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.auth.client.AccountDeletionBlockerClient;
import com.lp.auth.constant.UserStatus;
import com.lp.auth.dto.AccountDeletionBlockerView;
import com.lp.auth.entity.UserAccountCancellation;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.mapper.UserAccountCancellationMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Covers the scan, claim and skip branches of the execution job without a database or a live trade
 * service; the transaction manager is mocked, so the tests can assert commit versus rollback.
 *
 * @author Caspian
 * @since 0.3.0
 */
@ExtendWith(MockitoExtension.class)
class AccountCancellationExecutionJobTest {

    @Mock private UserAccountCancellationMapper cancellationMapper;

    @Mock private AuthUserMapper authUserMapper;

    @Mock private AccountDeletionBlockerClient blockerClient;

    @Mock private PlatformTransactionManager transactionManager;

    private SimpleMeterRegistry meterRegistry;
    private AccountCancellationExecutionJob job;

    /** Builds the job under test with in-memory metrics and a mock transaction manager. */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        job =
                new AccountCancellationExecutionJob(
                        cancellationMapper,
                        authUserMapper,
                        blockerClient,
                        new TransactionTemplate(transactionManager),
                        meterRegistry);
    }

    @Test
    void nothingHappensWhenNothingIsDue() {
        when(cancellationMapper.selectDue(any(), anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();

        verifyNoInteractions(blockerClient, authUserMapper);
    }

    @Test
    void blockedAndAlreadyClaimedRequestsAreSkipped() {
        givenDue(request(1L, 10L), request(2L, 20L));
        when(blockerClient.blockers(10L)).thenReturn(view(10L, true));
        when(blockerClient.blockers(20L)).thenReturn(view(20L, false));
        when(cancellationMapper.markExecuted(eq(2L), any())).thenReturn(0);

        assertThat(job.runOnce()).isZero();

        verify(cancellationMapper, never()).markExecuted(eq(1L), any());
        verifyNoInteractions(authUserMapper);
        assertThat(skipped("10")).isEqualTo(1.0);
        assertThat(skipped("20")).isEqualTo(1.0);
    }

    @Test
    void statusMismatchMarksTheClaimForRollback() {
        givenDue(request(1L, 10L));
        when(blockerClient.blockers(10L)).thenReturn(view(10L, false));
        when(cancellationMapper.markExecuted(eq(1L), any())).thenReturn(1);
        when(authUserMapper.updateStatusIfCurrent(
                        10L, UserStatus.COOLING_OFF, UserStatus.CANCELLED))
                .thenReturn(0);

        assertThat(job.runOnce()).isZero();

        assertThat(committed().isRollbackOnly()).isTrue();
        assertThat(counter(METRIC_EXECUTED)).isZero();
        assertThat(skipped("10")).isEqualTo(1.0);
    }

    @Test
    void dueRequestClaimsAndFlipsTheAccount() {
        givenDue(request(1L, 10L));
        when(blockerClient.blockers(10L)).thenReturn(view(10L, false));
        when(cancellationMapper.markExecuted(eq(1L), any())).thenReturn(1);
        when(authUserMapper.updateStatusIfCurrent(
                        10L, UserStatus.COOLING_OFF, UserStatus.CANCELLED))
                .thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);

        assertThat(committed().isRollbackOnly()).isFalse();
        assertThat(counter(METRIC_EXECUTED)).isEqualTo(1.0);
    }

    @Test
    void probeFailureCountsAsFailedAndTheBatchContinues() {
        givenDue(request(1L, 10L), request(2L, 20L));
        when(blockerClient.blockers(10L))
                .thenThrow(new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        when(blockerClient.blockers(20L)).thenReturn(view(20L, false));
        when(cancellationMapper.markExecuted(eq(2L), any())).thenReturn(1);
        when(authUserMapper.updateStatusIfCurrent(
                        20L, UserStatus.COOLING_OFF, UserStatus.CANCELLED))
                .thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);

        assertThat(meterRegistry.counter(METRIC_FAILED, "userId", "10").count()).isEqualTo(1.0);
        assertThat(counter(METRIC_EXECUTED)).isEqualTo(1.0);
    }

    private void givenDue(UserAccountCancellation... requests) {
        when(cancellationMapper.selectDue(any(), anyInt())).thenReturn(List.of(requests));
    }

    private TransactionStatus committed() {
        ArgumentCaptor<TransactionStatus> captor = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transactionManager).commit(captor.capture());
        return captor.getValue();
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }

    private double skipped(String userId) {
        return meterRegistry.counter(METRIC_SKIPPED, "userId", userId).count();
    }

    private static AccountDeletionBlockerView view(long userId, boolean blocked) {
        return new AccountDeletionBlockerView(userId, 0, 0, blocked);
    }

    private static UserAccountCancellation request(long id, long userId) {
        UserAccountCancellation request = new UserAccountCancellation();
        request.setId(id);
        request.setUserId(userId);
        return request;
    }
}
