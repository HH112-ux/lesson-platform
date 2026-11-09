package com.lp.trade.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Runs a write action at most once per user, endpoint and idempotency key.
 *
 * <p>The first caller takes a short lease, executes the action and stores the successful payload; a
 * concurrent caller with the same key waits briefly and then returns that payload. Business
 * failures are never cached, so a corrected retry can succeed. When Redis is unavailable the action
 * runs without the replay layer and the endpoints keep their database unique constraints as the
 * final guard; that degradation is logged and counted instead of being silent.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Service
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);

    private static final Duration LEASE_TTL = Duration.ofSeconds(120);

    private final IdempotencyStore store;

    private final ObjectMapper objectMapper;

    private final Duration waitTimeout;

    private final long pollMillis;

    private final Map<IdempotencyScope, Counter> degradedCounters =
            new EnumMap<>(IdempotencyScope.class);

    /**
     * Creates the service and registers one degradation counter per scope.
     *
     * @param store lease and result persistence
     * @param objectMapper serializer of cached payloads
     * @param meterRegistry registry the degradation counters are bound to
     * @param waitTimeoutMs how long a duplicate request waits for the first result
     * @param pollMillis interval between result polls while waiting
     * @author Caspian
     * @since 0.4.0
     */
    public IdempotencyService(
            IdempotencyStore store,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${lp.trade.idempotency.wait-timeout-ms:5000}") long waitTimeoutMs,
            @Value("${lp.trade.idempotency.poll-interval-ms:100}") long pollMillis) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.waitTimeout = Duration.ofMillis(waitTimeoutMs);
        this.pollMillis = pollMillis;
        for (IdempotencyScope scope : IdempotencyScope.values()) {
            degradedCounters.put(
                    scope,
                    Counter.builder("lp.trade.idempotency.degraded")
                            .description("Idempotency store failures that fell back to database")
                            .tag("scope", scope.slug())
                            .register(meterRegistry));
        }
    }

    /**
     * Executes the action once per key, or replays the first successful result.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id from the gateway
     * @param rawKey client supplied idempotency key
     * @param digest canonical request digest
     * @param resultType payload type used to deserialize a replay
     * @param action business action producing the payload
     * @param <T> payload type
     * @return payload together with the replay flag
     * @throws BizException with {@code 40001} for a malformed key, {@code 40901} when the key was
     *     first used with a different request, or {@code 40902} when the first request is still
     *     running after the wait window
     * @author Caspian
     * @since 0.4.0
     */
    public <T> IdempotencyOutcome<T> execute(
            IdempotencyScope scope,
            long userId,
            String rawKey,
            String digest,
            Class<T> resultType,
            Supplier<T> action) {
        String key = IdempotencyKeys.require(rawKey);
        String keyHash = IdempotencyDigests.sha256(key);

        Lookup<T> lookup = lookup(scope, userId, keyHash, digest, resultType);
        if (lookup.degraded()) {
            return degraded(scope, null, action);
        }
        if (lookup.replayed() != null) {
            return lookup.replayed();
        }

        String ownerToken = UUID.randomUUID().toString();
        Lease lease = acquire(scope, userId, keyHash, ownerToken);
        if (lease == Lease.UNAVAILABLE) {
            return degraded(scope, null, action);
        }
        if (lease == Lease.ACQUIRED) {
            return executeAsOwner(scope, userId, keyHash, ownerToken, digest, action);
        }

        long deadline = System.nanoTime() + waitTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            Lookup<T> polled = lookup(scope, userId, keyHash, digest, resultType);
            if (polled.degraded()) {
                return degraded(scope, null, action);
            }
            if (polled.replayed() != null) {
                return polled.replayed();
            }
            Lease retry = acquire(scope, userId, keyHash, ownerToken);
            if (retry == Lease.UNAVAILABLE) {
                return degraded(scope, null, action);
            }
            if (retry == Lease.ACQUIRED) {
                return executeAsOwner(scope, userId, keyHash, ownerToken, digest, action);
            }
        }
        throw new BizException(
                ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "the first request with this Idempotency-Key is still running");
    }

    private <T> IdempotencyOutcome<T> executeAsOwner(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            String ownerToken,
            String digest,
            Supplier<T> action) {
        try {
            T value = action.get();
            try {
                store.saveResult(
                        scope,
                        userId,
                        keyHash,
                        new StoredIdempotentResult(digest, objectMapper.writeValueAsString(value)),
                        scope.resultTtl());
            } catch (RuntimeException | JsonProcessingException exception) {
                // Replay is lost but the action already succeeded, so the response still stands.
                degrade(scope, exception);
            }
            return new IdempotencyOutcome<>(value, false);
        } finally {
            try {
                store.releaseLease(scope, userId, keyHash, ownerToken);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "idempotency lease release failed: scope={}, reason={}",
                        scope.slug(),
                        exception.toString());
            }
        }
    }

    private <T> Lookup<T> lookup(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            String digest,
            Class<T> resultType) {
        StoredIdempotentResult stored;
        try {
            Optional<StoredIdempotentResult> found = store.findResult(scope, userId, keyHash);
            stored = found.orElse(null);
        } catch (RuntimeException exception) {
            degrade(scope, exception);
            return new Lookup<>(null, true);
        }
        if (stored == null) {
            return new Lookup<>(null, false);
        }
        if (!stored.digest().equals(digest)) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT,
                    "Idempotency-Key was first used with a different request");
        }
        try {
            return new Lookup<>(
                    new IdempotencyOutcome<>(
                            objectMapper.readValue(stored.payload(), resultType), true),
                    false);
        } catch (JsonProcessingException exception) {
            degrade(scope, exception);
            return new Lookup<>(null, true);
        }
    }

    private Lease acquire(IdempotencyScope scope, long userId, String keyHash, String ownerToken) {
        try {
            return store.acquireLease(scope, userId, keyHash, ownerToken, LEASE_TTL)
                    ? Lease.ACQUIRED
                    : Lease.HELD;
        } catch (RuntimeException exception) {
            degrade(scope, exception);
            return Lease.UNAVAILABLE;
        }
    }

    private <T> IdempotencyOutcome<T> degraded(
            IdempotencyScope scope, RuntimeException cause, Supplier<T> action) {
        if (cause != null) {
            degrade(scope, cause);
        }
        return new IdempotencyOutcome<>(action.get(), false);
    }

    private void degrade(IdempotencyScope scope, Exception cause) {
        degradedCounters.get(scope).increment();
        if (cause == null) {
            LOG.warn(
                    "idempotency store degraded, using database guards only: scope={}",
                    scope.slug());
            return;
        }
        LOG.warn(
                "idempotency store degraded, using database guards only: scope={}, reason={}",
                scope.slug(),
                cause.toString());
    }

    private enum Lease {
        ACQUIRED,
        HELD,
        UNAVAILABLE
    }

    private record Lookup<T>(IdempotencyOutcome<T> replayed, boolean degraded) {}
}
