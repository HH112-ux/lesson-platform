package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.reconcile.ReconcilePageView;
import com.lp.trade.reconcile.ReconcilePermissions;
import com.lp.trade.reconcile.ReconcileView;
import com.lp.trade.service.ReconcileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Operations endpoints for payment reconciliation.
 *
 * <p>All endpoints require an {@code ADMIN} or {@code OPS} role plus the endpoint permission code.
 * The gateway injects both headers after JWT verification; a browser cannot forge them. Importing a
 * bill only records differences and never moves money.
 *
 * @author Caspian
 * @since 0.6.1
 */
@RestController
@RequestMapping("/api/v1/reconcile")
public class ReconcileController {

    private final ReconcileService reconcileService;

    /**
     * Creates the controller.
     *
     * @param reconcileService reconciliation service
     * @author Caspian
     * @since 0.6.1
     */
    public ReconcileController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    /**
     * Lists the reconciliation aggregates of one channel and bill day.
     *
     * @param roles gateway injected roles
     * @param permissions gateway injected permission codes
     * @param channel payment channel, defaults to 1 (Alipay)
     * @param billDate bill day in Asia/Shanghai, required
     * @param status optional status filter
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return aggregate page
     * @author Caspian
     * @since 0.6.1
     */
    @GetMapping("/daily")
    public Result<ReconcilePageView> daily(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "channel", defaultValue = "1") Integer channel,
            @RequestParam("billDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate billDate,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        ReconcilePermissions.require(roles, permissions, ReconcilePermissions.VIEW);
        return Result.ok(
                reconcileService.daily(channel, billDate, status, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Re-runs one stored bill day and returns the recomputed aggregate.
     *
     * @param roles gateway injected roles
     * @param permissions gateway injected permission codes
     * @param reconcileId primary key of the stored aggregate
     * @param request current request, used to resolve the trace id
     * @return recomputed aggregate
     * @author Caspian
     * @since 0.6.1
     */
    @PostMapping("/{reconcileId}/recheck")
    public Result<ReconcileView> recheck(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @PathVariable("reconcileId") long reconcileId,
            HttpServletRequest request) {
        ReconcilePermissions.require(roles, permissions, ReconcilePermissions.RECHECK);
        return Result.ok(
                ReconcileView.from(reconcileService.recheck(reconcileId)),
                TraceIds.resolve(request));
    }

    /**
     * Marks one pending aggregate as handled by the calling operator.
     *
     * @param roles gateway injected roles
     * @param permissions gateway injected permission codes
     * @param userId gateway injected operator id
     * @param reconcileId primary key of the stored aggregate
     * @param request current request, used to resolve the trace id
     * @return aggregate in status 3
     * @author Caspian
     * @since 0.6.1
     */
    @PostMapping("/{reconcileId}/handle")
    public Result<ReconcileView> handle(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("reconcileId") long reconcileId,
            HttpServletRequest request) {
        ReconcilePermissions.require(roles, permissions, ReconcilePermissions.HANDLE);
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(
                ReconcileView.from(reconcileService.handle(reconcileId, userId)),
                TraceIds.resolve(request));
    }

    /**
     * Imports one UTF-8 CSV bill for a bill day and bill type.
     *
     * <p>The header must be {@code tradeNo,merchantOrderNo,amount,occurredAt}; amounts are yuan
     * with two decimals and are always positive. Re-importing the same channel, day and type
     * overwrites the aggregate instead of adding duplicate differences.
     *
     * @param roles gateway injected roles
     * @param permissions gateway injected permission codes
     * @param channel payment channel, defaults to 1 (Alipay)
     * @param billDate bill day in Asia/Shanghai
     * @param billType 1 payment bill, 2 refund bill
     * @param file UTF-8 CSV file
     * @param request current request, used to resolve the trace id
     * @return persisted aggregate summary
     * @author Caspian
     * @since 0.6.1
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ReconcileView> importBill(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "channel", defaultValue = "1") Integer channel,
            @RequestParam("billDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate billDate,
            @RequestParam("billType") Integer billType,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        ReconcilePermissions.require(roles, permissions, ReconcilePermissions.HANDLE);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill file is required");
        }
        String csvText;
        try {
            csvText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill file cannot be read");
        }
        return Result.ok(
                ReconcileView.from(
                        reconcileService.importBill(channel, billDate, billType, csvText)),
                TraceIds.resolve(request));
    }
}
