package com.lp.trade.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis backed idempotency store.
 *
 * <p>Two keys per request: a short lived lease that names the executor, and a long lived result
 * that carries the first success. Acquiring the lease is one script that also refuses keys which
 * already carry a result, so a request that lost the race cannot run the action again. The lease is
 * released with compare-and-delete so a slow owner cannot delete a lease that has already expired
 * and been taken over.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "lp:trade:idempotency:";

    private static final String LEASE_SUFFIX = ":lease";

    private static final String RESULT_SUFFIX = ":result";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then"
                            + " return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('exists', KEYS[2]) == 1 then return 0 end "
                            + "if redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX') then "
                            + "return 1 else return 0 end",
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * Creates the store.
     *
     * @param redisTemplate shared Redis template
     * @param objectMapper serializer of cached payloads
     * @author Caspian
     * @since 0.4.0
     */
    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredIdempotentResult> findResult(
            IdempotencyScope scope, long userId, String keyHash) {
        String value = redisTemplate.opsForValue().get(resultKey(scope, userId, keyHash));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, StoredIdempotentResult.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("cached idempotency result is unreadable", exception);
        }
    }

    @Override
    public boolean acquireLease(
            IdempotencyScope scope, long userId, String keyHash, String ownerToken, Duration ttl) {
        Long acquired =
                redisTemplate.execute(
                        ACQUIRE_SCRIPT,
                        List.of(
                                leaseKey(scope, userId, keyHash),
                                resultKey(scope, userId, keyHash)),
                        ownerToken,
                        String.valueOf(ttl.toMillis()));
        return Long.valueOf(1L).equals(acquired);
    }

    @Override
    public void saveResult(
            IdempotencyScope scope,
            long userId,
            String keyHash,
            StoredIdempotentResult result,
            Duration ttl) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(
                            resultKey(scope, userId, keyHash),
                            objectMapper.writeValueAsString(result),
                            ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("idempotency result cannot be serialized", exception);
        }
    }

    @Override
    public void releaseLease(
            IdempotencyScope scope, long userId, String keyHash, String ownerToken) {
        redisTemplate.execute(
                RELEASE_SCRIPT, List.of(leaseKey(scope, userId, keyHash)), ownerToken);
    }

    private String leaseKey(IdempotencyScope scope, long userId, String keyHash) {
        return PREFIX + scope.slug() + ":" + userId + ":" + keyHash + LEASE_SUFFIX;
    }

    private String resultKey(IdempotencyScope scope, long userId, String keyHash) {
        return PREFIX + scope.slug() + ":" + userId + ":" + keyHash + RESULT_SUFFIX;
    }
}
