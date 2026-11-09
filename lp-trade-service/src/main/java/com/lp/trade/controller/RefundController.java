package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.RefundView;
import com.lp.trade.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buyer refund lookup.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param refundService refund operations
     * @author Caspian
     * @since 0.1.0
     */
    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * Returns one refund of the current buyer.
     *
     * @param userId gateway injected user id
     * @param refundId refund primary key
     * @param request current request, used to resolve the trace id
     * @return refund state
     * @throws BizException with {@code 40101} when the caller is anonymous
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{refundId}")
    public Result<RefundView> get(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("refundId") long refundId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(refundService.get(userId, refundId), TraceIds.resolve(request));
    }
}
