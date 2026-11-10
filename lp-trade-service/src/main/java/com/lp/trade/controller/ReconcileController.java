package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.reconcile.ReconcilePermissions;
import com.lp.trade.reconcile.ReconcileView;
import com.lp.trade.service.ReconcileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
