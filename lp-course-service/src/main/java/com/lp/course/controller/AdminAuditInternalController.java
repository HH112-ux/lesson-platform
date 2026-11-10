package com.lp.course.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service unified audit endpoint.
 *
 * <p>{@code POST} is used by the trade-service audit relay for refund decisions, because that state
 * change lives in {@code lp_oms} and cannot share a transaction with the audit table. Course and
 * teacher decisions do not use this endpoint; they append the row in-process inside their own
 * transaction.
 *
 * @author Caspian
 * @since 0.6.0
 */
@RestController
@RequestMapping("/internal/v1/audit-logs")
public class AdminAuditInternalController {

    private final AdminAuditService adminAuditService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminAuditService unified audit append use case
     * @author Caspian
     * @since 0.6.0
     */
    public AdminAuditInternalController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    /**
     * Appends one audit entry; redelivery with the same dedupe key is absorbed.
     *
     * @param body audit entry to append
     * @param request current request, used to resolve the trace id
     * @return empty successful envelope
     * @author Caspian
     * @since 0.6.0
     */
    @PostMapping
    public Result<Void> append(
            @RequestBody AuditLogCreateRequest body, HttpServletRequest request) {
        adminAuditService.record(body);
        return Result.ok(null, TraceIds.resolve(request));
    }
}
