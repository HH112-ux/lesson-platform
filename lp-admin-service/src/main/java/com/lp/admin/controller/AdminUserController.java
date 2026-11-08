package com.lp.admin.controller;

import com.lp.admin.dto.AdminUserPageView;
import com.lp.admin.service.AdminUserQueryService;
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
 * Operations console account list endpoint.
 *
 * <p>The gateway authenticates the caller and injects the roles; only {@code ADMIN} and {@code OPS}
 * may read accounts. Data stays in auth-service and is reached over the internal API.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminUserQueryService account list use case
     * @author Caspian
     * @since 0.2.0
     */
    public AdminUserController(AdminUserQueryService adminUserQueryService) {
        this.adminUserQueryService = adminUserQueryService;
    }

    /**
     * Lists accounts with keyword and status filters.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param keyword optional match on username, nickname or phone
     * @param status optional account status filter
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of accounts
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping
    public Result<AdminUserPageView> users(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.USER_LIST);
        return Result.ok(
                adminUserQueryService.page(keyword, status, pageNum, pageSize),
                TraceIds.resolve(request));
    }
}
