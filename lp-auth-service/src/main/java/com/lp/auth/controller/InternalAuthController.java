package com.lp.auth.controller;

import com.lp.auth.dto.InternalSessionRequest;
import com.lp.auth.dto.RefreshResponse;
import com.lp.auth.service.AuthService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal session issuance used by trade-service ticket redemption.
 *
 * <p>Every route under {@code /internal/v1/**} is guarded by {@code X-Internal-Token}; this class
 * deliberately has no user facing counterpart, so a browser can never mint a session for an
 * arbitrary user id.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/auth")
public class InternalAuthController {

    private final AuthService authService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param authService authentication use cases
     * @author Caspian
     * @since 0.1.0
     */
    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Issues a browser token pair for the ticket owner.
     *
     * @param body request carrying the user id proven by the ticket
     * @param request current request, used to resolve the trace id
     * @return a new access and refresh token pair
     * @throws BizException {@code 40001} when the user id is missing or not positive
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/session")
    public Result<RefreshResponse> session(
            @RequestBody InternalSessionRequest body, HttpServletRequest request) {
        if (body == null || body.getUserId() == null || body.getUserId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId is required");
        }
        return Result.ok(
                authService.issueSession(body.getUserId(), IdentityHeaders.SOURCE_WEB),
                TraceIds.resolve(request));
    }
}
