package com.lp.auth.controller;

import com.lp.auth.dto.AccountCancellationView;
import com.lp.auth.service.AccountCancellationService;
import com.lp.auth.service.impl.AccountCancellationServiceImpl;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account cancellation endpoints under {@code /api/v1/auth/account-cancellation}.
 *
 * <p>Every outcome, including a replayed application and a rejection, uses the unified envelope on
 * HTTP {@code 200}; the semantic result lives in the envelope code so clients never have to mix
 * transport status with business meaning. {@code POST} is idempotent while a request is running and
 * returns the same deadline without creating another row.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/auth/account-cancellation")
public class AccountCancellationController {

    private final AccountCancellationService cancellationService;

    /**
     * Creates the controller.
     *
     * @param cancellationService cancellation use cases
     * @author Caspian
     * @since 0.2.0
     */
    public AccountCancellationController(AccountCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    /**
     * Applies for cancellation, or replays the request already in progress.
     *
     * @param httpRequest current request carrying the gateway injected user id
     * @return successful envelope with the status, deadline and remaining seconds
     * @throws BizException {@code 40101} when the trusted user id header is absent or malformed,
     *     {@code 40301} for staff accounts, {@code 67003} when orders or refunds block the request,
     *     {@code 50401} when the trade probe is unavailable
     * @author Caspian
     * @since 0.2.0
     */
    @PostMapping
    public Result<AccountCancellationView> apply(HttpServletRequest httpRequest) {
        return Result.ok(
                cancellationService.apply(
                        userIdOf(httpRequest), AccountCancellationServiceImpl.TRIGGER_USER),
                TraceIds.resolve(httpRequest));
    }

    /**
     * Withdraws the cooling-off request.
     *
     * @param httpRequest current request carrying the gateway injected user id
     * @return successful envelope with an empty payload
     * @throws BizException {@code 40101} when the trusted user id header is absent or malformed,
     *     {@code 67004} when no cooling-off request exists
     * @author Caspian
     * @since 0.2.0
     */
    @DeleteMapping
    public Result<Void> withdraw(HttpServletRequest httpRequest) {
        cancellationService.withdraw(
                userIdOf(httpRequest), AccountCancellationServiceImpl.TRIGGER_USER);
        return Result.ok(null, TraceIds.resolve(httpRequest));
    }

    /**
     * Reports the most recent cancellation state so a client can render a countdown.
     *
     * @param httpRequest current request carrying the gateway injected user id
     * @return successful envelope with the status, deadline and remaining seconds
     * @throws BizException {@code 40101} when the trusted user id header is absent or malformed
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping
    public Result<AccountCancellationView> get(HttpServletRequest httpRequest) {
        return Result.ok(
                cancellationService.get(userIdOf(httpRequest)), TraceIds.resolve(httpRequest));
    }

    /**
     * Reads the trusted gateway user id, rejecting anything that is not a positive number.
     *
     * @param request current request
     * @return authenticated user id
     * @throws BizException {@code 40101} when the header is absent, blank or not numeric
     * @author Caspian
     * @since 0.2.0
     */
    private static long userIdOf(HttpServletRequest request) {
        String header = request.getHeader(IdentityHeaders.USER_ID);
        if (header == null || header.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
