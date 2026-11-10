package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.AccountDeletionBlockerView;
import com.lp.trade.service.AccountDeletionBlockerQuery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trade-side blocker lookup used by auth-service before it accepts a cancellation.
 *
 * <p>The {@code /internal/v1} prefix is neither routed nor whitelisted by the gateway and every
 * route below it is guarded by {@code X-Internal-Token}, so a browser cannot probe another user's
 * orders. A missing or forged token is rejected by {@code InternalTokenFilter} with {@code 40101},
 * which means this class never sees an unauthenticated caller.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/internal/v1/account-deletion")
public class InternalAccountDeletionController {

    private final AccountDeletionBlockerQuery blockerQuery;

    /**
     * Creates the controller with its query.
     *
     * @param blockerQuery blocker counts
     * @author Caspian
     * @since 0.2.0
     */
    public InternalAccountDeletionController(AccountDeletionBlockerQuery blockerQuery) {
        this.blockerQuery = blockerQuery;
    }

    /**
     * Counts orders and refunds that still block a cancellation.
     *
     * @param userId account owner, must be positive
     * @param request current request, used to resolve the trace id
     * @return blocker counts plus the aggregated decision
     * @throws BizException {@code 40001} when the user id is missing or not positive
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping("/blockers")
    public Result<AccountDeletionBlockerView> blockers(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId is required");
        }
        return Result.ok(blockerQuery.blockers(userId), TraceIds.resolve(request));
    }
}
