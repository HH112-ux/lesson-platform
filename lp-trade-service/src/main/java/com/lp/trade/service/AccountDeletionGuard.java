package com.lp.trade.service;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.AccountDeletionStateClient;
import com.lp.trade.config.TradeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Refuses new orders and payments while a buyer is inside the cancellation cooling-off period.
 *
 * <p>The answer comes from auth-service and is cached per user id for a short TTL, so a busy buyer
 * costs at most one internal call per TTL instead of one per request. Only successful answers are
 * cached; a failed probe is never remembered, and it fails closed with {@code 50401} because
 * accepting an order for an account that may be disappearing is worse than refusing checkout.
 *
 * <p>auth-service calls {@link #evict(long)} after a request commits, so the usual window is zero.
 * A failed invalidation leaves the cached answer until the TTL expires; that TTL is the documented
 * worst case window and the reason it is measured in tens of seconds rather than minutes.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Service
public class AccountDeletionGuard {

    /** Metric counting probes that could not produce an answer. */
    public static final String METRIC_DEGRADED = "lp.trade.order.account_guard.degraded";

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionGuard.class);

    /** Upper bound on cached buyers; entries are cheap and expire, so this is only a safety net. */
    private static final int MAX_CACHED_USERS = 10_000;

    private final AccountDeletionStateClient stateClient;

    private final TradeProperties properties;

    private final MeterRegistry meterRegistry;

    private final Map<Long, CachedState> cache = new ConcurrentHashMap<>();

    /**
     * Creates the guard with its collaborators.
     *
     * @param stateClient auth-service state reader
     * @param properties cache TTL
     * @param meterRegistry registry for the degraded counter
     * @author Caspian
     * @since 0.3.0
     */
    public AccountDeletionGuard(
            AccountDeletionStateClient stateClient,
            TradeProperties properties,
            MeterRegistry meterRegistry) {
        this.stateClient = stateClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Rejects the caller when the buyer is inside the cooling-off period.
     *
     * @param userId authenticated buyer id
     * @throws BizException {@code 67005} when the cooling-off period is active, {@code 50401} when
     *     the auth state probe fails
     * @author Caspian
     * @since 0.3.0
     */
    public void assertNotCoolingOff(long userId) {
        Boolean coolingOff = cachedCoolingOff(userId);
        if (coolingOff == null) {
            coolingOff = fetch(userId);
            cache.put(userId, new CachedState(coolingOff, expiryAfterTtl()));
            evictExpiredWhenFull();
        }
        if (coolingOff) {
            throw new BizException(ErrorCode.ACCOUNT_COOLING_OFF_RESTRICTED);
        }
    }

    /**
     * Drops the cached answer of one user; called by auth-service after a state change commits.
     *
     * @param userId account owner whose cached state must be dropped
     * @author Caspian
     * @since 0.3.0
     */
    public void evict(long userId) {
        cache.remove(userId);
    }

    private Boolean cachedCoolingOff(long userId) {
        CachedState entry = cache.get(userId);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtNanos() - System.nanoTime() <= 0) {
            cache.remove(userId);
            return null;
        }
        return entry.coolingOff();
    }

    private boolean fetch(long userId) {
        try {
            return stateClient.state(userId).coolingOff();
        } catch (BizException exception) {
            meterRegistry.counter(METRIC_DEGRADED).increment();
            LOG.warn("account deletion state probe failed, refusing checkout userId={}", userId);
            throw exception;
        }
    }

    private long expiryAfterTtl() {
        Duration ttl = properties.getAccountGuardCacheTtl();
        return System.nanoTime() + ttl.toNanos();
    }

    private void evictExpiredWhenFull() {
        if (cache.size() < MAX_CACHED_USERS) {
            return;
        }
        long now = System.nanoTime();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() - now <= 0);
        if (cache.size() >= MAX_CACHED_USERS) {
            // Every entry is still fresh; dropping them only costs one extra probe per buyer.
            cache.clear();
        }
    }

    /**
     * Cached answer with the monotonic instant it stops being valid.
     *
     * @param coolingOff answer from auth-service
     * @param expiresAtNanos {@link System#nanoTime()} based expiry
     * @author Caspian
     * @since 0.3.0
     */
    private record CachedState(boolean coolingOff, long expiresAtNanos) {}
}
