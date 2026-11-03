package com.lp.auth.service;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.MeResponse;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;

/**
 * Authentication use cases owned by the auth domain.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface AuthService {

    /**
     * Verifies the account password and issues a token pair.
     *
     * @param request validated login credentials
     * @param source source platform recorded with the refresh session
     * @return tokens plus the authenticated user summary
     * @author Caspian
     * @since 0.1.0
     */
    LoginResponse login(LoginRequest request, String source);

    /**
     * Rotates a refresh token into a new token pair.
     *
     * @param request the refresh token to consume
     * @return a new access token and a new refresh token
     * @author Caspian
     * @since 0.1.0
     */
    RefreshResponse refresh(RefreshRequest request);

    /**
     * Revokes the presented refresh token; repeated calls succeed.
     *
     * @param request the refresh token to revoke
     * @author Caspian
     * @since 0.1.0
     */
    void logout(LogoutRequest request);

    /**
     * Returns the current profile with effective roles and permissions.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @return profile, roles and permission codes
     * @author Caspian
     * @since 0.1.0
     */
    MeResponse me(long userId);
}
