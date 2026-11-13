package com.lp.trade.idempotency;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a write action at most once per user, endpoint and idempotency key.
 *
 * <p>The first caller takes a short lease, executes the action and stores the exact response body
 * it returned; a concurrent caller with the same key waits briefly and then receives that stored
 * body byte for byte, envelope included. Business failures are never cached, so a corrected retry
 * can succeed. When Redis is unavailable the action runs without the replay layer and the endpoints
 * keep their database unique constraints as the final guard; that degradation is logged and counted
 * instead of being silent.
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
     * @param objectMapper serializer of the response envelope
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
     * Executes the action once per key and returns the response body to write to the client.
     *
     * <p>The body is the serialized {@link Result} envelope produced by the first successful call,
     * so a replay must be written as is instead of being unwrapped and rebuilt.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id from the gateway
     * @param rawKey client supplied idempotency key
     * @param digest canonical request digest
     * @param traceId trace id embedded in the first response envelope
     * @param action business action producing the payload
     * @param <T> payload type
     * @return serialized response envelope together with the replay flag
     * @throws BizException with {@code 40001} for a malformed key, {@code 40901} when the key was
     *     first used with a different request, or {@code 40902} when the first request is still
     *     running after the wait window
     * @author Caspian
     * @since 0.4.0
     */
    public <T> IdempotencyOutcome<String> execute(
            IdempotencyScope scope,
            long userId,
            String rawKey,
            String digest,
            String traceId,
            Supplier<T> action) {
        String key = IdempotencyKeys.require(rawKey);
        String keyHash = IdempotencyDigests.sha256(key);

        Lookup lookup = lookup(scope, userId, keyHash, digest);
        if (lookup.degraded()) {
            return degraded(scope, traceId, null, action);
        }
        if (lookup.replayed() != null) {
            return lookup.replayed();
        }

        String ownerToken = UUID.randomUUID().toString();
        Lease lease = acquire(scope, userId, keyHash, ownerToken);
        if (lease == Lease.UNAVAILABLE) {
            return degraded(scope, traceId, null, action);
        }
        if (lease == Lease.ACQUIRED) {
            return executeAfterAcquire(scope, userId, keyHash, ownerToken, digest, traceId, action);
        }

        long deadline = System.nanoTime() + waitTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            Lookup polled = lookup(scope, userId, keyHash, digest);
            if (polled.degraded()) {
                return degraded(scope, traceId, null, action);
            }
            if (polled.replayed() != null) {
                return polled.replayed();
            }
            Lease retry = acquire(scope, userId, keyHash, ownerToken);
            if (retry == Lease.UNAVAILABLE) {
                return degraded(scope, traceId, null, action);
            }
            if (retry == Lease.ACQUIRED) {
                return executeAfterAcquire(
                        scope, userId, keyHash, ownerToken, digest, traceId, action);
            }
        }
        throw new BizException(
                ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "the first request with this Idempotency-Key is still running");
    }

    /**
     * Rechecks the stored result before running the action under a freshly acquired lease.
     *
     * <p>The previous owner stores its result before releasing the lease, so a request can win the
     * lease and only then see that result. Running the action in that window would overwrite the
     * first result, therefore the lease is dropped and the stored response replayed instead.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id
     * @param keyHash SHA-256 hash of the client key
     * @param ownerToken token of the lease this request just acquired
     * @param digest canonical request digest
     * @param traceId trace id used when the action must still run
     * @param action business action producing the payload
     * @param <T> payload type
     * @return replay of the stored result, or the outcome of running the action
     * @author Caspian
     * @since 0.4.0
     */
    private <T> IdempotencyOutcome<String> executeAfterAcquire(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            String ownerToken,
            String digest,
            String traceId,
            Supplier<T> action) {
        Lookup confirmed;
        try {
            confirmed = lookup(scope, userId, keyHash, digest);
        } catch (RuntimeException exception) {
            releaseQuietly(scope, userId, keyHash, ownerToken);
            throw exception;
        }
        if (confirmed.replayed() != null) {
            releaseQuietly(scope, userId, keyHash, ownerToken);
            return confirmed.replayed();
        }
        if (confirmed.degraded()) {
            releaseQuietly(scope, userId, keyHash, ownerToken);
            return degraded(scope, traceId, null, action);
        }
        return executeAsOwner(scope, userId, keyHash, ownerToken, digest, traceId, action);
    }

    private <T> IdempotencyOutcome<String> executeAsOwner(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            String ownerToken,
            String digest,
            String traceId,
            Supplier<T> action) {
        try {
            String body = serialize(action.get(), traceId);
            try {
                store.saveResult(
                        scope,
                        userId,
                        keyHash,
                        new StoredIdempotentResult(digest, body),
                        scope.resultTtl());
            } catch (RuntimeException exception) {
                // Replay is lost but the action already succeeded, so the response still stands.
                degrade(scope, exception);
            }
            return new IdempotencyOutcome<>(body, false);
        } finally {
            releaseQuietly(scope, userId, keyHash, ownerToken);
        }
    }

    private void releaseQuietly(
            IdempotencyScope scope, long userId, String keyHash, String ownerToken) {
        try {
            store.releaseLease(scope, userId, keyHash, ownerToken);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "idempotency lease release failed: scope={}, reason={}",
                    scope.slug(),
                    exception.toString());
        }
    }

    private Lookup lookup(IdempotencyScope scope, long userId, String keyHash, String digest) {
        StoredIdempotentResult stored;
        try {
            Optional<StoredIdempotentResult> found = store.findResult(scope, userId, keyHash);
            stored = found.orElse(null);
        } catch (RuntimeException exception) {
            degrade(scope, exception);
            return new Lookup(null, true);
        }
        if (stored == null) {
            return new Lookup(null, false);
        }
        if (!stored.digest().equals(digest)) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT,
                    "Idempotency-Key was first used with a different request");
        }
        return new Lookup(new IdempotencyOutcome<>(stored.payload(), true), false);
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

    private <T> IdempotencyOutcome<String> degraded(
            IdempotencyScope scope, String traceId, RuntimeException cause, Supplier<T> action) {
        if (cause != null) {
            degrade(scope, cause);
        }
        return new IdempotencyOutcome<>(serialize(action.get(), traceId), false);
    }

    private <T> String serialize(T value, String traceId) {
        try {
            return objectMapper.writeValueAsString(Result.ok(value, traceId));
        } catch (JacksonException exception) {
            throw new IllegalStateException("idempotent response cannot be serialized", exception);
        }
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

    private record Lookup(IdempotencyOutcome<String> replayed, boolean degraded) {}
}
