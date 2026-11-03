package com.lp.auth.service;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;

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
     * @return tokens plus the authenticated user summary
     * @author Caspian
     * @since 0.1.0
     */
    LoginResponse login(LoginRequest request);
}
