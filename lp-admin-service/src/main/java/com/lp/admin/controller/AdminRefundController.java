package com.lp.admin.controller;

import com.lp.admin.dto.AdminChannelRefundStatusView;
import com.lp.admin.dto.AdminRefundPageView;
import com.lp.admin.dto.AdminRefundReviewRequest;
import com.lp.admin.dto.AdminRefundView;
import com.lp.admin.service.AdminRefundService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations console refund review endpoints.
 *
 * <p>Only {@code ADMIN} and {@code OPS} holding {@code admin:order:list} may read the queue or
 * decide a request. The reviewer id comes from the gateway injected header, never from the body.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final AdminRefundService adminRefundService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminRefundService refund review use case
     * @author Caspian
     * @since 0.1.0
     */
    public AdminRefundController(AdminRefundService adminRefundService) {
        this.adminRefundService = adminRefundService;
    }

    /**
     * Lists refunds with an optional status filter.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param status optional refund status
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of refunds
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<AdminRefundPageView> list(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.ORDER_LIST);
        return Result.ok(
                adminRefundService.page(status, pageNum, pageSize), TraceIds.resolve(request));
    }

    /**
     * Reads one refund back from the channel for the console detail panel.
     *
     * <p>Read-only: it lets operations see what the channel reports for a refund that is stuck
     * locally, and never changes the refund. A missing channel record is returned as {@code
     * NOT_FOUND} inside the body, not as an HTTP {@code 404}.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param refundId refund primary key
     * @param request current request, used to resolve the trace id
     * @return channel snapshot plus the local reconciliation bookkeeping
     * @author Caspian
     * @since 0.9.0
     */
    @GetMapping("/{refundId}/channel")
    public Result<AdminChannelRefundStatusView> channel(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @PathVariable("refundId") long refundId,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.ORDER_LIST);
        return Result.ok(adminRefundService.channelStatus(refundId), TraceIds.resolve(request));
    }

    /**
     * Approves or rejects one pending refund.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param userId gateway injected operations user id
     * @param refundId refund primary key
     * @param body decision and optional rejection reason
     * @param request current request, used to resolve the trace id
     * @return refund state after the decision
     * @throws BizException with {@code 40101} when the reviewer is anonymous
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{refundId}/review")
    public Result<AdminRefundView> review(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("refundId") long refundId,
            @RequestBody(required = false) AdminRefundReviewRequest body,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.ORDER_LIST);
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (body == null || body.approved() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "approved is required");
        }
        int operatorRole = ConsoleRoles.operatorRole(roles);
        return Result.ok(
                adminRefundService.review(
                        refundId, body.approved(), body.rejectReason(), userId, operatorRole),
                TraceIds.resolve(request));
    }
}
