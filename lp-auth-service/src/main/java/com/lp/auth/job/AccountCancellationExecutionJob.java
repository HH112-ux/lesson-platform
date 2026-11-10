package com.lp.auth.job;

import com.lp.auth.client.AccountDeletionBlockerClient;
import com.lp.auth.constant.UserStatus;
import com.lp.auth.entity.UserAccountCancellation;
import com.lp.auth.mapper.AuthUserMapper;
import com.lp.auth.mapper.UserAccountCancellationMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes account cancellations whose cooling-off period has ended.
 *
 * <p>Execution claims the request ({@code status 0 -> 2}) and flips the account ({@code
 * sys_user.status 2 -> 3}) in one transaction, so concurrent replicas cancel each account once and
 * the losers count as skipped. The blocker probe runs before the claim; the bean stays disabled
 * until anonymization lands, otherwise an account would reach status 3 with its personal data still
 * readable.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Component
@ConditionalOnProperty(
        name = "lp.auth.account-deletion.execution-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AccountCancellationExecutionJob {

    private static final Logger LOG =
            LoggerFactory.getLogger(AccountCancellationExecutionJob.class);

    /** Counts executed requests; the alarm on unexpected silence is built on this counter. */
    public static final String METRIC_EXECUTED = "lp.auth.account_cancellation.executed";

    /** Counts due requests left for a later scan, tagged with the account owner. */
    public static final String METRIC_SKIPPED = "lp.auth.account_cancellation.execute.skipped";

    /** Counts failed executions that will be retried by the next scan, tagged with the owner. */
    public static final String METRIC_FAILED = "lp.auth.account_cancellation.execute.failed";

    private static final int BATCH_SIZE = 200;

    private final UserAccountCancellationMapper cancellationMapper;

    private final AuthUserMapper authUserMapper;

    private final AccountDeletionBlockerClient blockerClient;

    private final TransactionTemplate transactionTemplate;

    private final MeterRegistry meterRegistry;

    /**
     * Creates the job with its persistence, probe, transaction and metric dependencies.
     *
     * @param cancellationMapper cancellation request persistence
     * @param authUserMapper account persistence
     * @param blockerClient trade-side blocker probe
     * @param transactionTemplate one transaction per request
     * @param meterRegistry registry for executed, skipped and failed counters
     * @author Caspian
     * @since 0.3.0
     */
    public AccountCancellationExecutionJob(
            UserAccountCancellationMapper cancellationMapper,
            AuthUserMapper authUserMapper,
            AccountDeletionBlockerClient blockerClient,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry) {
        this.cancellationMapper = cancellationMapper;
        this.authUserMapper = authUserMapper;
        this.blockerClient = blockerClient;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs one scan on the configured schedule.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Scheduled(
            fixedDelayString = "${lp.auth.account-deletion.scan-delay-ms:60000}",
            initialDelayString = "${lp.auth.account-deletion.scan-initial-delay-ms:30000}")
    public void scan() {
        runOnce();
    }

    /**
     * Executes one batch of due requests.
     *
     * <p>Each request is isolated: a probe failure or a database error counts as failed for that
     * account and the batch continues, so one broken row cannot hold back everybody else. A failure
     * of the scan query itself propagates to the scheduler, which retries on the next cycle.
     *
     * @return number of requests executed by this call
     * @author Caspian
     * @since 0.3.0
     */
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<UserAccountCancellation> due = cancellationMapper.selectDue(now, BATCH_SIZE);
        int executed = 0;
        for (UserAccountCancellation request : due) {
            try {
                if (executeOne(request, now)) {
                    executed++;
                }
            } catch (RuntimeException exception) {
                meterRegistry
                        .counter(METRIC_FAILED, "userId", String.valueOf(request.getUserId()))
                        .increment();
                LOG.error(
                        "account cancellation failed: userId={}, requestId={}, reason={}",
                        request.getUserId(),
                        request.getId(),
                        exception.getMessage());
            }
        }
        return executed;
    }

    /**
     * Executes one due request after probing its trade-side blockers.
     *
     * <p>The claim and the status flip share a transaction: when the account is no longer in the
     * cooling-off period the claim is rolled back, so a later scan still sees the request as active
     * and the user keeps the deadline already promised.
     *
     * @param request due cancellation request
     * @param now execution time
     * @return {@code true} when this call executed the request
     * @author Caspian
     * @since 0.3.0
     */
    boolean executeOne(UserAccountCancellation request, LocalDateTime now) {
        long userId = request.getUserId();
        if (blockerClient.blockers(userId).blocked()) {
            skip(userId, request.getId(), "blocked");
            return false;
        }
        Boolean executed =
                transactionTemplate.execute(
                        status -> {
                            if (cancellationMapper.markExecuted(request.getId(), now) == 0) {
                                skip(userId, request.getId(), "already-claimed");
                                return Boolean.FALSE;
                            }
                            if (authUserMapper.updateStatusIfCurrent(
                                            userId, UserStatus.COOLING_OFF, UserStatus.CANCELLED)
                                    == 0) {
                                status.setRollbackOnly();
                                skip(userId, request.getId(), "status-changed");
                                return Boolean.FALSE;
                            }
                            return Boolean.TRUE;
                        });
        if (Boolean.TRUE.equals(executed)) {
            meterRegistry.counter(METRIC_EXECUTED).increment();
            LOG.info(
                    "account cancellation executed: userId={}, requestId={}, effectiveAt={}",
                    userId,
                    request.getId(),
                    request.getEffectiveAt());
            return true;
        }
        return false;
    }

    private void skip(long userId, Long requestId, String reason) {
        meterRegistry.counter(METRIC_SKIPPED, "userId", String.valueOf(userId)).increment();
        LOG.warn(
                "account cancellation skipped: userId={}, requestId={}, reason={}",
                userId,
                requestId,
                reason);
    }
}
