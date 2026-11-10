package com.lp.auth.controller;

import com.lp.auth.dto.AccountDeletionStateView;
import com.lp.auth.service.AccountCancellationService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cooling-off state read by other services before they accept a purchase.
 *
 * <p>The {@code /internal/v1} prefix is unreachable from the gateway and every route below it is
 * guarded by {@code X-Internal-Token}, so a browser cannot ask whether another account is being
 * deleted. A missing or forged token is rejected by {@code InternalTokenFilter} with {@code 40101}.
 *
 * @author Caspian
 * @since 0.3.0
 */
@RestController
@RequestMapping("/internal/v1/account-deletion")
public class InternalAccountCancellationController {

    private final AccountCancellationService cancellationService;

    /**
     * Creates the controller with its service.
     *
     * @param cancellationService cancellation use cases
     * @author Caspian
     * @since 0.3.0
     */
    public InternalAccountCancellationController(AccountCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    /**
     * Reports whether an account is inside the cancellation cooling-off period.
     *
     * @param userId account owner, must be positive
     * @param request current request, used to resolve the trace id
     * @return cooling-off decision plus the deadline when one is active
     * @throws BizException {@code 40001} when the user id is missing or not positive
     * @author Caspian
     * @since 0.3.0
     */
    @GetMapping("/state")
    public Result<AccountDeletionStateView> state(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId is required");
        }
        return Result.ok(cancellationService.state(userId), TraceIds.resolve(request));
    }
}
