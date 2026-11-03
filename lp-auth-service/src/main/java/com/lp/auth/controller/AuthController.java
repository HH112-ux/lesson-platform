package com.lp.auth.controller;

import com.lp.auth.dto.LoginRequest;
import com.lp.auth.dto.LoginResponse;
import com.lp.auth.service.AuthService;
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
 * <p>This milestone exposes login only. Register, refresh, logout and miniapp login arrive
 * with their own slices. Failures are translated into the unified envelope by
 * {@code GlobalExceptionHandler}.</p>
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
     * @param request      validated login body
     * @param httpRequest  current request, used for the trace id
     * @return successful envelope carrying tokens and the user summary
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request), TraceIds.resolve(httpRequest));
    }
}
