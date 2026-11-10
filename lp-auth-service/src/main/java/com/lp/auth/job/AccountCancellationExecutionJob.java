package com.lp.auth.job;

import com.lp.auth.client.AccountDeletionBlockerClient;
import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.entity.UserAccountCancellation;
import com.lp.auth.mapper.UserAccountCancellationMapper;
import com.lp.auth.service.AccountAnonymizationService;
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
 * <p>Execution claims the request ({@code status 0 -> 2}) and anonymises the account in one
 * transaction, so concurrent replicas cancel each account once and the losers count as skipped. The
 * anonymisation calls the trade service inside the same transaction: a remote failure rolls the
 * claim back, leaves the request for the next scan and every write is idempotent, so the retry
 * cannot hash a value twice. The blocker probe runs before the claim.
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

    private final AccountDeletionBlockerClient blockerClient;

    private final AccountAnonymizationService anonymizationService;

    private final TransactionTemplate transactionTemplate;

    private final MeterRegistry meterRegistry;

    /**
     * Creates the job with its persistence, probe, anonymisation, transaction and metric
     * dependencies.
     *
     * @param cancellationMapper cancellation request persistence
     * @param blockerClient trade-side blocker probe
     * @param anonymizationService account and payment anonymisation
     * @param properties cancellation settings carrying the anonymisation salt
     * @param transactionTemplate one transaction per request
     * @param meterRegistry registry for executed, skipped and failed counters
     * @throws IllegalStateException when the anonymisation salt is blank, because a digest without
     *     a secret salt is reversible and a missing salt must fail at startup, not mid-execution
     * @author Caspian
     * @since 0.3.0
     */
    public AccountCancellationExecutionJob(
            UserAccountCancellationMapper cancellationMapper,
            AccountDeletionBlockerClient blockerClient,
            AccountAnonymizationService anonymizationService,
            AccountDeletionProperties properties,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry) {
        String salt = properties.getAnonymizeSalt();
        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "lp.auth.account-deletion.anonymize-salt is required when the execution job is"
                            + " enabled");
        }
        this.cancellationMapper = cancellationMapper;
        this.blockerClient = blockerClient;
        this.anonymizationService = anonymizationService;
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
     * <p>The claim and the anonymisation share a transaction: when the account is no longer in the
     * cooling-off period, or the trade call fails, everything is rolled back, so a later scan still
     * sees the request as active and the user keeps the deadline already promised.
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
                            if (!anonymizationService.anonymize(userId)) {
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
