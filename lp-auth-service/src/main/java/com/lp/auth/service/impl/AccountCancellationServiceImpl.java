package com.lp.auth.service.impl;

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
import com.lp.auth.service.AccountCancellationService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs the account cancellation request flow inside {@code lp_ums}.
 *
 * <p>The deadline is calculated once when the request is created and stored, so a later
 * configuration change cannot move a promise already made. A repeated request while one is running
 * returns the stored deadline instead of creating a second row, and the unique generated column
 * stays the final authority for concurrent inserts. Staff accounts are refused because courses,
 * audits and settlements must keep a named owner.
 *
 * <p>Blockers live in the trade domain, so this service asks the trade internal API instead of
 * reading trade tables. That probe fails closed: an unusable answer is surfaced as {@code 50401}
 * and no request row is written.
 *
 * <p>A successful apply or withdraw also drops the trade-side cached state. The call is registered
 * for after commit and runs on a background thread, so a rollback cannot clear a cache that still
 * matches the database and the user's response never waits for the invalidation.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class AccountCancellationServiceImpl implements AccountCancellationService {

    /** Metric counting requests that created a new cooling-off row. */
    public static final String METRIC_APPLIED = "lp.auth.account_cancellation.applied";

    /** Metric counting withdrawn cooling-off requests. */
    public static final String METRIC_WITHDRAWN = "lp.auth.account_cancellation.withdrawn";

    /** Trigger source recorded for a request that originated from the account owner. */
    public static final String TRIGGER_USER = "user";

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Set<String> STAFF_ROLES = Set.of("TEACHER", "OPS", "ADMIN");

    private static final Logger LOG = LoggerFactory.getLogger(AccountCancellationServiceImpl.class);

    private final AuthUserMapper authUserMapper;

    private final UserAccountCancellationMapper cancellationMapper;

    private final AccountDeletionBlockerClient blockerClient;

    private final AccountDeletionCacheClient cacheClient;

    private final AccountDeletionProperties properties;

    private final MeterRegistry meterRegistry;

    private final Clock clock;

    /**
     * Creates the service with its collaborators.
     *
     * @param authUserMapper account status and role queries
     * @param cancellationMapper cancellation request persistence
     * @param blockerClient trade-side blocker probe
     * @param cacheClient trade-side cooling-off cache invalidation
     * @param properties cooling-off period and trade probe settings
     * @param meterRegistry registry for applied and withdrawn counters
     * @param clock clock used to fix deadlines and remaining seconds
     * @author Caspian
     * @since 0.2.0
     */
    public AccountCancellationServiceImpl(
            AuthUserMapper authUserMapper,
            UserAccountCancellationMapper cancellationMapper,
            AccountDeletionBlockerClient blockerClient,
            AccountDeletionCacheClient cacheClient,
            AccountDeletionProperties properties,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.authUserMapper = authUserMapper;
        this.cancellationMapper = cancellationMapper;
        this.blockerClient = blockerClient;
        this.cacheClient = cacheClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Applies for cancellation or replays the request already in progress.
     *
     * <p>The sequence is: reject unusable accounts, reject staff accounts, return an existing
     * request unchanged, probe trade blockers, then insert and switch {@code sys_user.status} to
     * the cooling-off value in one transaction. A concurrent duplicate loses on the unique
     * generated column and is answered with the winner's deadline, so the client sees one stable
     * value.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @param source trigger source recorded in logs, {@code user} for this entry point
     * @return the deadline and status of the request, identical to the existing one on replay
     * @throws BizException {@code 40101} when the account is missing, {@code 67001} when it was
     *     cancelled, {@code 40301} when it is disabled or holds a staff role, {@code 67003} when
     *     live orders or refunds block the request, {@code 50401} when the blocker probe fails
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    @Transactional
    public AccountCancellationView apply(long userId, String source) {
        UserAccount account = authUserMapper.selectById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        requireCancellableStatus(account);
        requireNoStaffRole(userId);

        UserAccountCancellation existing = cancellationMapper.selectActiveByUserId(userId);
        if (existing != null) {
            LOG.info(
                    "account cancellation apply replayed userId={} effectiveAt={} source={}",
                    userId,
                    existing.getEffectiveAt(),
                    source);
            invalidateTradeCacheAfterCommit(userId);
            return viewOf(existing, now());
        }

        AccountDeletionBlockerView blockers = blockerClient.blockers(userId);
        if (blockers.blocked()) {
            throw new BizException(
                    ErrorCode.ACCOUNT_CANCELLATION_BLOCKED,
                    "pending orders="
                            + blockers.pendingOrders()
                            + ", refunding requests="
                            + blockers.refundingRequests());
        }

        LocalDateTime requestedAt = now();
        UserAccountCancellation request = new UserAccountCancellation();
        request.setUserId(userId);
        request.setStatus(AccountCancellationStatus.COOLING_OFF);
        request.setRequestedAt(requestedAt);
        request.setEffectiveAt(requestedAt.plus(properties.getCoolingOffPeriod()));
        try {
            cancellationMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            UserAccountCancellation winner = cancellationMapper.selectActiveByUserId(userId);
            if (winner == null) {
                throw exception;
            }
            LOG.info(
                    "account cancellation apply lost a race userId={} effectiveAt={} source={}",
                    userId,
                    winner.getEffectiveAt(),
                    source);
            return viewOf(winner, requestedAt);
        }

        authUserMapper.updateStatusIfCurrent(userId, UserStatus.ACTIVE, UserStatus.COOLING_OFF);
        meterRegistry.counter(METRIC_APPLIED).increment();
        invalidateTradeCacheAfterCommit(userId);
        LOG.info(
                "account cancellation applied userId={} effectiveAt={} source={}",
                userId,
                request.getEffectiveAt(),
                source);
        return viewOf(request, requestedAt);
    }

    /**
     * Withdraws the cooling-off request and restores the account status.
     *
     * <p>The conditional update on {@code status = 0} makes two concurrent withdrawals safe: the
     * loser sees zero affected rows and is answered with {@code 67004}, the same code as a user who
     * never applied. Nothing changes when there is no active request.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @param source trigger source recorded in logs, {@code user} for this entry point
     * @throws BizException {@code 67004} when no cooling-off request exists
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    @Transactional
    public void withdraw(long userId, String source) {
        UserAccountCancellation active = cancellationMapper.selectActiveByUserId(userId);
        if (active == null) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLATION_NOT_FOUND);
        }
        LocalDateTime withdrawnAt = now();
        if (cancellationMapper.withdraw(active.getId(), withdrawnAt) == 0) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLATION_NOT_FOUND);
        }
        authUserMapper.updateStatusIfCurrent(userId, UserStatus.COOLING_OFF, UserStatus.ACTIVE);
        meterRegistry.counter(METRIC_WITHDRAWN).increment();
        invalidateTradeCacheAfterCommit(userId);
        LOG.info(
                "account cancellation withdrawn userId={} effectiveAt={} source={}",
                userId,
                active.getEffectiveAt(),
                source);
    }

    /**
     * Reports the most recent cancellation state of a user.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @return status, deadline and remaining seconds; empty values when never applied
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public AccountCancellationView get(long userId) {
        UserAccountCancellation latest = cancellationMapper.selectLatestByUserId(userId);
        if (latest == null) {
            return new AccountCancellationView(null, null, 0);
        }
        return viewOf(latest, now());
    }

    /**
     * Reports whether a user is inside the cooling-off period for other services.
     *
     * @param userId account owner
     * @return cooling-off decision plus the deadline when one is active
     * @author Caspian
     * @since 0.3.0
     */
    @Override
    public AccountDeletionStateView state(long userId) {
        UserAccountCancellation latest = cancellationMapper.selectLatestByUserId(userId);
        boolean coolingOff =
                latest != null
                        && latest.getStatus() != null
                        && latest.getStatus() == AccountCancellationStatus.COOLING_OFF;
        return new AccountDeletionStateView(
                userId,
                coolingOff,
                coolingOff && latest.getEffectiveAt() != null
                        ? latest.getEffectiveAt().format(DEADLINE_FORMAT)
                        : null);
    }

    /**
     * Registers the trade cache invalidation to run after the current transaction commits.
     *
     * <p>Registering instead of calling directly keeps a rolled back request from clearing the
     * cache, and the invalidation client hands the call to a background thread, so the response
     * never waits for trade. Outside a transaction the call is made immediately, which is correct
     * because there is no commit to wait for.
     *
     * @param userId account owner whose cached state must be dropped
     * @author Caspian
     * @since 0.3.0
     */
    private void invalidateTradeCacheAfterCommit(long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheClient.invalidate(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheClient.invalidate(userId);
                    }
                });
    }

    /**
     * Rejects accounts that cannot start a cancellation.
     *
     * @param account account row loaded from {@code lp_ums}
     * @throws BizException {@code 67001} when the account was cancelled, {@code 40301} when it is
     *     disabled
     * @author Caspian
     * @since 0.2.0
     */
    private static void requireCancellableStatus(UserAccount account) {
        Integer status = account.getStatus();
        if (status != null && status == UserStatus.CANCELLED) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
        }
        if (status == null || (status != UserStatus.ACTIVE && status != UserStatus.COOLING_OFF)) {
            throw new BizException(ErrorCode.FORBIDDEN, "account disabled");
        }
    }

    /**
     * Rejects staff accounts so courses, audits and settlements keep a named owner.
     *
     * @param userId authentic user id
     * @throws BizException {@code 40301} when the account holds a staff role
     * @author Caspian
     * @since 0.2.0
     */
    private void requireNoStaffRole(long userId) {
        List<String> roles = authUserMapper.selectRoleCodes(userId);
        boolean staff = roles.stream().anyMatch(STAFF_ROLES::contains);
        if (staff) {
            throw new BizException(
                    ErrorCode.FORBIDDEN, "staff accounts cannot be cancelled; contact operations");
        }
    }

    /**
     * Builds the response view for a stored request.
     *
     * @param request request row
     * @param at instant used to calculate the remaining seconds
     * @return status, ISO deadline and remaining seconds, zero unless still cooling off
     * @author Caspian
     * @since 0.2.0
     */
    private static AccountCancellationView viewOf(
            UserAccountCancellation request, LocalDateTime at) {
        long remaining = 0;
        if (request.getStatus() != null
                && request.getStatus() == AccountCancellationStatus.COOLING_OFF) {
            remaining = Math.max(0, Duration.between(at, request.getEffectiveAt()).getSeconds());
        }
        return new AccountCancellationView(
                request.getStatus(),
                request.getEffectiveAt() == null
                        ? null
                        : request.getEffectiveAt().format(DEADLINE_FORMAT),
                remaining);
    }

    /**
     * Returns the current time truncated to milliseconds, matching the {@code DATETIME(3)} columns.
     *
     * @return current local time with millisecond precision
     * @author Caspian
     * @since 0.2.0
     */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
