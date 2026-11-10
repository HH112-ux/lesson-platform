package com.lp.trade.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Verifies the atomic acquire contract of {@link RedisIdempotencyStore}.
 *
 * <p>The store delegates the lease decision to a single Lua script that also inspects the result
 * key. A script reply of {@code 0} covers both a lease held by another request and a key that
 * already has a stored result; either way the caller must fall back to waiting.
 *
 * @author Caspian
 * @since 0.4.0
 */
class RedisIdempotencyStoreTest {

    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void refusesTheLeaseWhenTheScriptReportsAStoredResult() {
        assertFalse(acquireWith(redisWithReply(0L)));
    }

    @Test
    void acquiresTheLeaseWhenTheScriptClaimsIt() {
        assertTrue(acquireWith(redisWithReply(1L)));
    }

    @Test
    void treatsAMissingScriptReplyAsALostLease() {
        assertFalse(acquireWith(redisWithReply(null)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void passesBothKeysToTheScriptThatChecksForAStoredResult() {
        StringRedisTemplate redis = redisWithReply(0L);

        acquireWith(redis);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), keys.capture(), any(Object[].class));

        assertEquals(2, keys.getValue().size());
        assertTrue(keys.getValue().get(0).endsWith(":lease"));
        assertTrue(keys.getValue().get(1).endsWith(":result"));
        assertTrue(script.getValue().getScriptAsString().contains("exists', KEYS[2]"));
    }

    private boolean acquireWith(StringRedisTemplate redis) {
        return new RedisIdempotencyStore(redis, new ObjectMapper())
                .acquireLease(IdempotencyScope.PAYMENT_INITIATE, 7L, "hash", "owner-token", TTL);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private StringRedisTemplate redisWithReply(Long scriptReply) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(scriptReply)
                .when(redis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        return redis;
    }
}
