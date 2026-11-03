package com.lp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/logout}.
 *
 * <p>The refresh token is the credential, so logout works even after the access token expired. The
 * endpoint is idempotent: revoking an unknown or already revoked token still succeeds.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LogoutRequest {

    @NotBlank(message = "refreshToken is required")
    private String refreshToken;

    /**
     * Returns the refresh token to revoke.
     *
     * @return opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets the refresh token to revoke.
     *
     * @param refreshToken opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
