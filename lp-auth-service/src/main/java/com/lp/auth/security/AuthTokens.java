package com.lp.auth.security;

/**
 * Tokens returned to a client after a successful login.
 *
 * <p>The access token is a signed JWT valid for a short window. The refresh token is an opaque
 * random value; its hashed Redis entry is written by the caller right after issuance.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class AuthTokens {

    private final String accessToken;

    private final String refreshToken;

    private final long expiresInSeconds;

    /**
     * Creates an immutable token pair.
     *
     * @param accessToken      signed JWT access token
     * @param refreshToken     opaque refresh token
     * @param expiresInSeconds access token lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresInSeconds = expiresInSeconds;
    }

    /**
     * Returns the signed access token.
     *
     * @return JWT compact serialization
     * @author Caspian
     * @since 0.1.0
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Returns the opaque refresh token.
     *
     * @return random token value
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Returns the access token lifetime in seconds.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
