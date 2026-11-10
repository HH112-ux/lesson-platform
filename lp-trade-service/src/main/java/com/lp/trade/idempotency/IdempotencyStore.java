package com.lp.trade.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Persistence port for idempotency leases and replayed results.
 *
 * <p>Implementations may fail at any time; callers treat every runtime failure as unavailable and
 * fall back to the database level guards of each endpoint.
 *
 * @author Caspian
 * @since 0.4.0
 */
public interface IdempotencyStore {

    /**
     * Looks up the cached first result.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id
     * @param keyHash SHA-256 hash of the client key
     * @return cached result, or empty when the key was never completed
     * @author Caspian
     * @since 0.4.0
     */
    Optional<StoredIdempotentResult> findResult(
            IdempotencyScope scope, long userId, String keyHash);

    /**
     * Tries to become the single executor for a key.
     *
     * <p>The check and the write are atomic: a key that already has a stored result can never be
     * leased again, so a late lease attempt observes the finished request instead of overwriting
     * it.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id
     * @param keyHash SHA-256 hash of the client key
     * @param ownerToken token identifying the executor
     * @param ttl lease lifetime; an expired lease may be taken over
     * @return true when the lease was acquired, false when another request holds it or the key
     *     already has a stored result
     * @author Caspian
     * @since 0.4.0
     */
    boolean acquireLease(
            IdempotencyScope scope, long userId, String keyHash, String ownerToken, Duration ttl);

    /**
     * Stores the successful first result for later replays.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id
     * @param keyHash SHA-256 hash of the client key
     * @param result digest and serialized payload
     * @param ttl cache lifetime
     * @author Caspian
     * @since 0.4.0
     */
    void saveResult(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            StoredIdempotentResult result,
            Duration ttl);

    /**
     * Releases a lease only when the caller still owns it.
     *
     * @param scope endpoint scope
     * @param userId authenticated buyer id
     * @param keyHash SHA-256 hash of the client key
     * @param ownerToken token supplied to {@link #acquireLease}
     * @author Caspian
     * @since 0.4.0
     */
    void releaseLease(IdempotencyScope scope, long userId, String keyHash, String ownerToken);
}
