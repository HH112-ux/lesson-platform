package com.lp.auth.security;

import com.lp.auth.config.JwtProperties;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis backed refresh session store.
 *
 * <p>Keys follow {@code lp:auth:refresh:{sha256(token)}} and expire together with the refresh
 * token, so an abandoned session disappears without a cleanup job. An unreachable Redis fails the
 * request instead of silently accepting unvalidated tokens.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    /** Key prefix reserved for refresh sessions of the auth domain. */
    public static final String KEY_PREFIX = "lp:auth:refresh:";

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenStore.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final Duration ttl;

    /**
     * Creates the store.
     *
     * @param redisTemplate string template backed by the shared connection factory
     * @param objectMapper serializer for the session payload
     * @param properties token settings supplying the refresh lifetime
     * @author Caspian
     * @since 0.1.0
     */
    public RedisRefreshTokenStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(properties.getRefreshTokenTtlSeconds());
    }

    /**
     * Writes the session under the hashed token key.
     *
     * @param refreshToken raw refresh token
     * @param session session metadata
     * @throws BizException {@code 50001} when Redis is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void store(String refreshToken, RefreshSession session) {
        try {
            redisTemplate.opsForValue().set(keyOf(refreshToken), serialize(session), ttl);
        } catch (DataAccessException ex) {
            throw unavailable("store", ex);
        }
    }

    /**
     * Atomically removes and returns the session.
     *
     * @param refreshToken raw refresh token
     * @return the session, or empty when the key is absent
     * @throws BizException {@code 50001} when Redis is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Optional<RefreshSession> consume(String refreshToken) {
        String payload;
        try {
            payload = redisTemplate.opsForValue().getAndDelete(keyOf(refreshToken));
        } catch (DataAccessException ex) {
            throw unavailable("consume", ex);
        }
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, RefreshSession.class));
        } catch (JacksonException ex) {
            log.error("refresh session payload is unreadable; treating it as revoked", ex);
            return Optional.empty();
        }
    }

    /**
     * Deletes the session key; a missing key is a no-op.
     *
     * @param refreshToken raw refresh token
     * @throws BizException {@code 50001} when Redis is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void revoke(String refreshToken) {
        try {
            redisTemplate.delete(keyOf(refreshToken));
        } catch (DataAccessException ex) {
            throw unavailable("revoke", ex);
        }
    }

    /**
     * Builds the Redis key from the SHA-256 digest of the raw token.
     *
     * @param refreshToken raw refresh token
     * @return key such as {@code lp:auth:refresh:ab12...}
     * @author Caspian
     * @since 0.1.0
     */
    static String keyOf(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /**
     * Serializes a session to JSON.
     *
     * @param session session metadata
     * @return JSON payload
     * @author Caspian
     * @since 0.1.0
     */
    private String serialize(RefreshSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JacksonException ex) {
            throw new IllegalStateException("cannot serialize the refresh session", ex);
        }
    }

    /**
     * Logs the dependency failure and builds the error raised to the caller.
     *
     * @param operation failed operation name, never the token itself
     * @param ex Redis failure
     * @return never returns; always throws
     * @author Caspian
     * @since 0.1.0
     */
    private BizException unavailable(String operation, DataAccessException ex) {
        log.error(
                "refresh token store unavailable operation={} cause={}",
                operation,
                ex.getMessage());
        return new BizException(ErrorCode.INTERNAL_ERROR, "refresh token store unavailable");
    }
}
