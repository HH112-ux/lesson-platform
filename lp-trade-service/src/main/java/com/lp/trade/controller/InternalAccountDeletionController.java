package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.AccountAnonymizationCommand;
import com.lp.trade.dto.AccountAnonymizationView;
import com.lp.trade.dto.AccountDeletionBlockerView;
import com.lp.trade.service.AccountAnonymizationService;
import com.lp.trade.service.AccountDeletionBlockerQuery;
import com.lp.trade.service.AccountDeletionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trade-side account deletion support used by auth-service.
 *
 * <p>Exposes the blocker lookup accepted before a cancellation, the payer identifier rewrite of an
 * executed cancellation and the cache invalidation sent after one commits. The {@code /internal/v1}
 * prefix is neither routed nor whitelisted by the gateway and every route below it is guarded by
 * {@code X-Internal-Token}, so a browser cannot probe another user's orders. A missing or forged
 * token is rejected by {@code InternalTokenFilter} with {@code 40101}, which means this class never
 * sees an unauthenticated caller.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/internal/v1/account-deletion")
public class InternalAccountDeletionController {

    private final AccountDeletionBlockerQuery blockerQuery;

    private final AccountDeletionGuard guard;

    private final AccountAnonymizationService anonymizationService;

    /**
     * Creates the controller with its collaborators.
     *
     * @param blockerQuery blocker counts
     * @param guard cached cooling-off state to invalidate
     * @param anonymizationService payer identifier rewrite
     * @author Caspian
     * @since 0.2.0
     */
    public InternalAccountDeletionController(
            AccountDeletionBlockerQuery blockerQuery,
            AccountDeletionGuard guard,
            AccountAnonymizationService anonymizationService) {
        this.blockerQuery = blockerQuery;
        this.guard = guard;
        this.anonymizationService = anonymizationService;
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

    /**
     * Overwrites the payer identifiers of an account whose cancellation has just been executed.
     *
     * <p>auth-service calls this inside the transaction that also anonymises the account, and rolls
     * that transaction back when this call fails, so the request stays retryable. The salt arrives
     * in the body rather than in the query string so it never reaches an access log.
     *
     * @param command account owner plus the salt owned by auth-service
     * @param request current request, used to resolve the trace id
     * @return number of payment rows rewritten by this call, {@code 0} on a repeated call
     * @throws BizException {@code 40001} when the user id is missing, not positive, or the salt is
     *     blank
     * @author Caspian
     * @since 0.3.0
     */
    @PostMapping("/anonymize")
    public Result<AccountAnonymizationView> anonymize(
            @RequestBody(required = false) AccountAnonymizationCommand command,
            HttpServletRequest request) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId is required");
        }
        if (command.salt() == null || command.salt().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "salt is required");
        }
        return Result.ok(
                anonymizationService.anonymize(command.userId(), command.salt()),
                TraceIds.resolve(request));
    }

    /**
     * Drops the cached cooling-off state of one buyer.
     *
     * <p>auth-service calls this after an apply or withdraw commits. Missing entries are not an
     * error: the cache is best effort and the TTL covers anything this call could not reach.
     *
     * @param userId account owner, must be positive
     * @param request current request, used to resolve the trace id
     * @return successful envelope with an empty payload
     * @throws BizException {@code 40001} when the user id is missing or not positive
     * @author Caspian
     * @since 0.3.0
     */
    @DeleteMapping("/state")
    public Result<Void> evictState(
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId is required");
        }
        guard.evict(userId);
        return Result.ok(null, TraceIds.resolve(request));
    }
}
