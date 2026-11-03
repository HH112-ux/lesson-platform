package com.lp.auth.controller;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.dto.LogoutRequest;
import com.lp.auth.dto.RefreshRequest;
import com.lp.auth.dto.RefreshResponse;
import com.lp.auth.service.AuthService;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints under {@code /api/v1/auth}.
 *
 * <p>Login, refresh and logout are available. Register and miniapp login arrive with their own
 * slices. Failures are translated into the unified envelope by {@code GlobalExceptionHandler}.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the controller.
     *
     * @param authService authentication use cases
     * @author Caspian
     * @since 0.1.0
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Verifies credentials and returns a token pair.
     *
     * @param request     validated login body
     * @param httpRequest current request, used for the source platform and trace id
     * @return successful envelope carrying tokens and the user summary
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request, sourceOf(httpRequest)),
                TraceIds.resolve(httpRequest));
    }

    /**
     * Rotates a refresh token into a new token pair.
     *
     * @param request     validated refresh body
     * @param httpRequest current request, used for the trace id
     * @return successful envelope carrying the new token pair
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/refresh")
    public Result<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                           HttpServletRequest httpRequest) {
        return Result.ok(authService.refresh(request), TraceIds.resolve(httpRequest));
    }

    /**
     * Revokes the presented refresh token; repeated calls succeed.
     *
     * @param request     validated logout body
     * @param httpRequest current request, used for the trace id
     * @return successful envelope with an empty payload
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutRequest request,
                               HttpServletRequest httpRequest) {
        authService.logout(request);
        return Result.ok(null, TraceIds.resolve(httpRequest));
    }

    /**
     * Reads the source platform injected by the gateway, defaulting to web.
     *
     * <p>Inbound values are stripped by the gateway, so a value present here was set upstream.
     * Anything outside the known set falls back to {@code web} rather than being trusted.</p>
     *
     * @param request current request
     * @return {@code miniapp}, {@code admin} or {@code web}
     * @author Caspian
     * @since 0.1.0
     */
    private static String sourceOf(HttpServletRequest request) {
        String source = request.getHeader(IdentityHeaders.USER_SOURCE);
        if (IdentityHeaders.SOURCE_MINIAPP.equals(source)
                || IdentityHeaders.SOURCE_ADMIN.equals(source)) {
            return source;
        }
        return IdentityHeaders.SOURCE_WEB;
    }
}
