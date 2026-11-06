package com.lp.admin.controller;

import com.lp.admin.dto.AdminMetricsOverviewView;
import com.lp.admin.service.AdminDashboardService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations console overview metrics endpoint.
 *
 * <p>Only {@code ADMIN} and {@code OPS} may read the figures. The window is fixed to the last seven
 * days because the merged console has no date picker yet.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
public class AdminMetricsController {

    private final AdminDashboardService adminDashboardService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminDashboardService dashboard use case
     * @author Caspian
     * @since 0.2.0
     */
    public AdminMetricsController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * Returns the overview figures of the last seven days.
     *
     * @param roles gateway injected roles, comma separated
     * @param request current request, used to resolve the trace id
     * @return revenue, conversion, signups, best sellers and course counters
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping("/overview")
    public Result<AdminMetricsOverviewView> overview(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        return Result.ok(adminDashboardService.overview(), TraceIds.resolve(request));
    }
}
