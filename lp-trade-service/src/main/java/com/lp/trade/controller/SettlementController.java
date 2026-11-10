package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.reconcile.CloseSettlementRequest;
import com.lp.trade.reconcile.ReconcilePermissions;
import com.lp.trade.reconcile.SettlementView;
import com.lp.trade.reconcile.VoidSettlementRequest;
import com.lp.trade.service.SettlementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations endpoints that close or void one settled bill day of the Alipay channel.
 *
 * <p>A settlement closes one bill day and stores its net summary; it never moves money. Both
 * endpoints require the {@code admin:reconcile:close} permission, which only {@code ADMIN} holds;
 * void additionally requires the {@code ADMIN} role itself and a non-empty reason. The gateway
 * injects the role and permission headers after JWT verification, so a browser cannot forge them.
 *
 * @author Caspian
 * @since 0.6.2
 */
@RestController
@RequestMapping("/api/v1/reconcile/settlements")
public class SettlementController {

    private static final int DEFAULT_CHANNEL = 1;

    private final SettlementService settlementService;

    /**
     * Creates the controller.
     *
     * @param settlementService day-end settlement service
     * @author Caspian
     * @since 0.6.2
     */
    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * Closes one bill day and returns the stored batch.
     *
     * @param roles gateway injected roles
     * @param permissions gateway injected permission codes
     * @param userId gateway injected operator id
     * @param body channel and bill day to close
     * @param request current request, used to resolve the trace id
     * @return closed batch, or the existing batch when the day was already closed
     * @author Caspian
     * @since 0.6.2
     */
    @PostMapping
    public Result<SettlementView> close(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestBody CloseSettlementRequest body,
            HttpServletRequest request) {
        ReconcilePermissions.require(roles, permissions, ReconcilePermissions.CLOSE);
        long closedBy = requireUserId(userId);
        Integer channel = body.channel() == null ? DEFAULT_CHANNEL : body.channel();
        return Result.ok(
                SettlementView.from(settlementService.close(channel, body.billDate(), closedBy)),
                TraceIds.resolve(request));
    }

    /**
     * Voids a closed batch, unlocks its bill day and records the reason in the unified audit.
     *
     * @param roles gateway injected roles, must contain {@code ADMIN}
     * @param permissions gateway injected permission codes
     * @param userId gateway injected operator id
     * @param batchId primary key of the batch
     * @param body mandatory void reason
     * @param request current request, used to resolve the trace id
     * @return voided batch
     * @author Caspian
     * @since 0.6.2
     */
    @PostMapping("/{batchId}/void")
    public Result<SettlementView> voidSettleBatch(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("batchId") long batchId,
            @RequestBody VoidSettlementRequest body,
            HttpServletRequest request) {
        ReconcilePermissions.requireAdmin(roles, permissions, ReconcilePermissions.CLOSE);
        long voidBy = requireUserId(userId);
        String traceId = TraceIds.resolve(request);
        return Result.ok(
                SettlementView.from(
                        settlementService.voidSettleBatch(batchId, voidBy, body.reason(), traceId)),
                traceId);
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
