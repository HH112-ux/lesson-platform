package com.lp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/refresh}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefreshRequest {

    @NotBlank(message = "refreshToken is required")
    private String refreshToken;

    /**
     * Returns the refresh token to rotate.
     *
     * @return opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets the refresh token to rotate.
     *
     * @param refreshToken opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
