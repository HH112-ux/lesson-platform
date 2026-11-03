package com.lp.auth.dto;

/**
 * Login response body of {@code POST /api/v1/auth/miniapp-login}.
 *
 * <p>The token pair is identical in shape to {@link LoginResponse}; {@code isNewUser} tells the
 * client whether it should show onboarding. Neither the openid nor the WeChat session key is
 * exposed here.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class MiniappLoginResponse {

    private final String accessToken;

    private final String refreshToken;

    private final long expiresIn;

    private final boolean newUser;

    private final LoginUser user;

    /**
     * Creates the response.
     *
     * @param accessToken signed access token
     * @param refreshToken refresh token stored in the whitelist
     * @param expiresIn access token lifetime in seconds
     * @param newUser whether the openid created an account on this call
     * @param user authenticated user summary
     * @author Caspian
     * @since 0.1.0
     */
    public MiniappLoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            boolean newUser,
            LoginUser user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.newUser = newUser;
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
     * Returns the refresh token.
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
     * Returns whether the account was created on this call.
     *
     * @return {@code true} for a first login of this openid
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isNewUser() {
        return newUser;
    }

    /**
     * Returns the authenticated user summary.
     *
     * @return user summary with roles
     * @author Caspian
     * @since 0.1.0
     */
    public LoginUser getUser() {
        return user;
    }
}
