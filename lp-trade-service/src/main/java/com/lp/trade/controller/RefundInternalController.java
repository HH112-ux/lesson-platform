package com.lp.trade.controller;

import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.ChannelRefundStatusView;
import com.lp.trade.dto.RefundAdminPageView;
import com.lp.trade.dto.RefundReviewRequest;
import com.lp.trade.dto.RefundView;
import com.lp.trade.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal refund API used only by the admin service.
 *
 * <p>The gateway never exposes {@code /internal/v1/**}; the shared internal token filter rejects
 * public callers. The reviewer id is supplied by the admin service from the authenticated console
 * session, never by the browser.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/refunds")
public class RefundInternalController {

    private final RefundService refundService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param refundService refund operations
     * @author Caspian
     * @since 0.1.0
     */
    public RefundInternalController(RefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * Lists refunds for the operations review queue.
     *
     * @param status optional status filter
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return refund page
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<RefundAdminPageView> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(refundService.list(status, pageNum, pageSize), TraceIds.resolve(request));
    }

    /**
     * Reads one refund back from the channel for the operations console.
     *
     * <p>The call is read-only by design: it lets an operator see what the channel reports for a
     * refund that is stuck locally, and never changes the refund. A missing channel record or a
     * failed query is returned as an explicit outcome instead of {@code 404}.
     *
     * @param refundId refund primary key
     * @param request current request, used to resolve the trace id
     * @return channel snapshot plus the local reconciliation bookkeeping
     * @throws BizException with {@code 40401} when the refund is absent
     * @author Caspian
     * @since 0.9.0
     */
    @GetMapping("/{refundId}/channel")
    public Result<ChannelRefundStatusView> channel(
            @PathVariable("refundId") long refundId, HttpServletRequest request) {
        return Result.ok(refundService.channelStatus(refundId), TraceIds.resolve(request));
    }

    /**
     * Approves or rejects one pending refund.
     *
     * @param refundId refund primary key
     * @param body decision, required rejection reason, reviewer id and role
     * @param request current request, used to resolve the trace id
     * @return refund state after the decision
     * @throws BizException with {@code 40001} when the decision or reviewer is missing
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{refundId}/review")
    public Result<RefundView> review(
            @PathVariable("refundId") long refundId,
            @RequestBody(required = false) RefundReviewRequest body,
            HttpServletRequest request) {
        if (body == null || body.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "approved is required");
        }
        if (body.getReviewerId() == null || body.getReviewerId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reviewerId is required");
        }
        if (!OperatorRole.isValid(body.getOperatorRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "operatorRole is invalid");
        }
        String traceId = TraceIds.resolve(request);
        return Result.ok(
                refundService.review(
                        refundId,
                        body.getApproved(),
                        body.getRejectReason(),
                        body.getReviewerId(),
                        body.getOperatorRole(),
                        traceId),
                traceId);
    }
}
