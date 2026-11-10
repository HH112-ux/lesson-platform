package com.lp.trade.idempotency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
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

    @Test
    void refusesTheLeaseWhenTheScriptReportsAStoredResult() {
        assertFalse(acquireWith(0L));
    }

    @Test
    void acquiresTheLeaseWhenTheScriptClaimsIt() {
        assertTrue(acquireWith(1L));
    }

    @Test
    void treatsAMissingScriptReplyAsALostLease() {
        assertFalse(acquireWith(null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean acquireWith(Long scriptReply) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(scriptReply)
                .when(redis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        return new RedisIdempotencyStore(redis, new ObjectMapper())
                .acquireLease(
                        IdempotencyScope.PAYMENT_INITIATE,
                        7L,
                        "hash",
                        "owner-token",
                        Duration.ofSeconds(30));
    }
}
