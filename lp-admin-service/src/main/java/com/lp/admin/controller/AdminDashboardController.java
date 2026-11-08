package com.lp.admin.controller;

import com.lp.admin.dto.AdminDashboardView;
import com.lp.admin.service.AdminDashboardService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations dashboard endpoint.
 *
 * <p>Figures are aggregated from course-service, trade-service and auth-service over their internal
 * APIs; admin-service owns no data of its own.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminDashboardService dashboard use case
     * @author Caspian
     * @since 0.1.0
     */
    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * Returns the current dashboard figures.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param request current request, used to resolve the trace id
     * @return course, order and account totals
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<AdminDashboardView> dashboard(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            HttpServletRequest request) {
        requireConsoleRole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.DASHBOARD_VIEW);
        return Result.ok(adminDashboardService.load(), TraceIds.resolve(request));
    }

    /**
     * Rejects callers that do not belong to the operations console.
     *
     * @param roles gateway injected roles
     * @author Caspian
     * @since 0.1.0
     */
    private void requireConsoleRole(String roles) {
        if (roles == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean allowed =
                Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .anyMatch(role -> "ADMIN".equals(role) || "OPS".equals(role));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
