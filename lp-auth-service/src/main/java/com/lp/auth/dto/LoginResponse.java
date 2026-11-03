package com.lp.auth.dto;

/**
 * Response body of {@code POST /api/v1/auth/login}.
 *
 * <p>Matches the contract in {@code docs/engineering-plan.md} 5.3:
 * {@code {accessToken,refreshToken,expiresIn,user:{id,nickname,avatarUrl,roles[]}}}.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LoginResponse {

    private final String accessToken;

    private final String refreshToken;

    private final long expiresIn;

    private final LoginUser user;

    /**
     * Creates an immutable login response.
     *
     * @param accessToken  signed access token
     * @param refreshToken opaque refresh token
     * @param expiresIn    access token lifetime in seconds
     * @param user         authenticated user summary
     * @author Caspian
     * @since 0.1.0
     */
    public LoginResponse(String accessToken, String refreshToken, long expiresIn, LoginUser user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
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
     * Returns the opaque refresh token.
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

    /**
     * Returns the authenticated user summary.
     *
     * @return user summary
     * @author Caspian
     * @since 0.1.0
     */
    public LoginUser getUser() {
        return user;
    }
}
