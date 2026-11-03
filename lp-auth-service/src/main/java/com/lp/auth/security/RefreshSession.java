package com.lp.auth.security;

import java.time.Instant;

/**
 * Refresh session persisted in Redis behind a hashed refresh token key.
 *
 * <p>The raw refresh token is never stored: the Redis key is the SHA-256 digest of the token and
 * the value only carries the owning user, the issue time and the source platform. This keeps a
 * Redis dump or key listing from exposing usable tokens.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefreshSession {

    private long userId;

    private Instant issuedAt;

    private String source;

    /**
     * Creates an empty session; required by the JSON mapper.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public RefreshSession() {
    }

    /**
     * Creates a session for a freshly issued refresh token.
     *
     * @param userId   owning user id
     * @param issuedAt issue time
     * @param source   source platform: web, miniapp or admin
     * @author Caspian
     * @since 0.1.0
     */
    public RefreshSession(long userId, Instant issuedAt, String source) {
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.source = source;
    }

    /**
     * Returns the owning user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public long getUserId() {
        return userId;
    }

    /**
     * Sets the owning user id.
     *
     * @param userId user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(long userId) {
        this.userId = userId;
    }

    /**
     * Returns the issue time.
     *
     * @return issue time
     * @author Caspian
     * @since 0.1.0
     */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * Sets the issue time.
     *
     * @param issuedAt issue time
     * @author Caspian
     * @since 0.1.0
     */
    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    /**
     * Returns the source platform.
     *
     * @return web, miniapp or admin
     * @author Caspian
     * @since 0.1.0
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets the source platform.
     *
     * @param source web, miniapp or admin
     * @author Caspian
     * @since 0.1.0
     */
    public void setSource(String source) {
        this.source = source;
    }
}
