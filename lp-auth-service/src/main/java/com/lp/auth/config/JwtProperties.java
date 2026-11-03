package com.lp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for access token issuance.
 *
 * <p>The private key path must point to an unencrypted PKCS#8 PEM file. Issuer and lifetimes
 * follow {@code docs/engineering-plan.md} 6.1: access tokens live 15 minutes, refresh tokens
 * are kept for 7 days.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.auth.jwt")
public class JwtProperties {

    /** Default token issuer. */
    public static final String DEFAULT_ISSUER = "auth-service";

    /** Default access token lifetime in seconds. */
    public static final long DEFAULT_ACCESS_TTL_SECONDS = 900L;

    /** Default refresh token lifetime in seconds. */
    public static final long DEFAULT_REFRESH_TTL_SECONDS = 604800L;

    private String issuer = DEFAULT_ISSUER;

    private long accessTokenTtlSeconds = DEFAULT_ACCESS_TTL_SECONDS;

    private long refreshTokenTtlSeconds = DEFAULT_REFRESH_TTL_SECONDS;

    private String privateKeyPath = "";

    /**
     * Returns the token issuer claim.
     *
     * @return issuer value
     * @author Caspian
     * @since 0.1.0
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Sets the token issuer claim.
     *
     * @param issuer issuer value
     * @author Caspian
     * @since 0.1.0
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * Returns the access token lifetime in seconds.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /**
     * Sets the access token lifetime in seconds.
     *
     * @param accessTokenTtlSeconds lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /**
     * Returns the refresh token lifetime in seconds.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    /**
     * Sets the refresh token lifetime in seconds.
     *
     * @param refreshTokenTtlSeconds lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    /**
     * Returns the PEM path of the RS256 private key.
     *
     * @return filesystem path, blank when not configured
     * @author Caspian
     * @since 0.1.0
     */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /**
     * Sets the PEM path of the RS256 private key.
     *
     * @param privateKeyPath filesystem path
     * @author Caspian
     * @since 0.1.0
     */
    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }
}
