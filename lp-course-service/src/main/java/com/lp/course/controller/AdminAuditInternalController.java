package com.lp.course.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.dto.AuditLogPageView;
import com.lp.course.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service unified audit endpoints.
 *
 * <p>{@code POST} is used by the trade-service audit relay for refund decisions, because that state
 * change lives in {@code lp_oms} and cannot share a transaction with the audit table. Course and
 * teacher decisions do not use this endpoint; they append the row in-process inside their own
 * transaction. {@code GET} serves the console query through admin-service.
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
     * @param adminAuditService unified audit append and query use cases
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

    /**
     * Reads one page in object mode or operator plus time range mode.
     *
     * @param objectType object type code, paired with {@code objectId}
     * @param objectId object primary key, paired with {@code objectType}
     * @param operatorId operator user id, used when object mode is absent
     * @param from inclusive lower bound, ISO date-time, used in operator mode
     * @param to exclusive upper bound, ISO date-time, used in operator mode
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of audit entries
     * @author Caspian
     * @since 0.6.0
     */
    @GetMapping
    public Result<AuditLogPageView> page(
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                adminAuditService.page(
                        objectType, objectId, operatorId, from, to, pageNum, pageSize),
                TraceIds.resolve(request));
    }
}
