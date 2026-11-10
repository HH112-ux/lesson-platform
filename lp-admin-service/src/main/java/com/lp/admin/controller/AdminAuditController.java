package com.lp.admin.controller;

import com.lp.admin.dto.AdminAuditLogPageView;
import com.lp.admin.service.AdminAuditQueryService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations console endpoints for the unified audit log.
 *
 * <p>Only console users holding {@code admin:audit:list} may read the log. Two retrieval shapes are
 * supported and at least one must be complete: by object ({@code objectType} plus {@code
 * objectId}), or by operator ({@code operatorId}) inside a half-open range {@code [from, to)}.
 * Paging follows {@code pageNum} and {@code pageSize}.
 *
 * @author Caspian
 * @since 0.6.0
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditQueryService adminAuditQueryService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminAuditQueryService audit query use case
     * @author Caspian
     * @since 0.6.0
     */
    public AdminAuditController(AdminAuditQueryService adminAuditQueryService) {
        this.adminAuditQueryService = adminAuditQueryService;
    }

    /**
     * Reads one page of audit entries.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param objectType object type code, 0 course, 1 refund, 2 teacher
     * @param objectId reviewed object primary key
     * @param operatorId operator user id
     * @param from inclusive lower bound, ISO local date-time
     * @param to exclusive upper bound, ISO local date-time
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of audit entries
     * @author Caspian
     * @since 0.6.0
     */
    @GetMapping
    public Result<AdminAuditLogPageView> page(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.AUDIT_LIST);
        return Result.ok(
                adminAuditQueryService.page(
                        objectType, objectId, operatorId, from, to, pageNum, pageSize),
                TraceIds.resolve(request));
    }
}
