package com.lp.auth.security;

import java.util.Optional;

/**
 * Stores and revokes refresh sessions.
 *
 * <p>The store is the single source of truth for refresh token validity: a token is usable only
 * while its entry exists. {@link #consume(String)} is an atomic read and delete, so a recovery
 * attempt can never reuse the same token twice.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface RefreshTokenStore {

    /**
     * Persists a refresh session with the configured refresh token lifetime.
     *
     * @param refreshToken raw token returned to the client, hashed before storage
     * @param session      session metadata, never the raw token
     * @throws com.lp.common.exception.BizException {@code 50001} when the store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    void store(String refreshToken, RefreshSession session);

    /**
     * Atomically reads and deletes a refresh session.
     *
     * @param refreshToken raw token presented by the client
     * @return the stored session, or empty when the token is unknown or already rotated
     * @throws com.lp.common.exception.BizException {@code 50001} when the store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    Optional<RefreshSession> consume(String refreshToken);

    /**
     * Deletes a refresh session; a missing entry is not an error.
     *
     * @param refreshToken raw token presented by the client
     * @throws com.lp.common.exception.BizException {@code 50001} when the store is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    void revoke(String refreshToken);
}
