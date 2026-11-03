package com.lp.auth.dto;

/**
 * Response body of {@code POST /api/v1/auth/refresh}.
 *
 * <p>Rotation returns a complete new pair: the presented refresh token is already invalid, so
 * the client must replace both values it holds.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefreshResponse {

    private final String accessToken;

    private final String refreshToken;

    private final long expiresIn;

    /**
     * Creates an immutable refresh response.
     *
     * @param accessToken  signed access token
     * @param refreshToken new opaque refresh token
     * @param expiresIn    access token lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    /**
     * Returns the signed access token.
     *
     * @return access token
     * @author Caspian
     * @since 0.1.0
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Returns the new opaque refresh token.
     *
     * @return refresh token
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
    public long getExpiresIn() {
        return expiresIn;
    }
}
