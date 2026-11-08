package com.lp.admin.controller;

import com.lp.admin.dto.AdminMenuView;
import com.lp.admin.service.AdminMenuService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations console menu endpoint.
 *
 * <p>The gateway resolves the caller's permission codes from the JWT and forwards them in {@code
 * X-User-Permissions}; this controller authorises the console role and lets the service filter the
 * tree, so the front end never receives a node it cannot open.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminMenuService menu tree builder
     * @author Caspian
     * @since 0.1.0
     */
    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    /**
     * Returns the menu tree visible to the caller.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param request current request, used to resolve the trace id
     * @return filtered menu tree
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<List<AdminMenuView>> menus(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermissions(permissions);
        return Result.ok(
                adminMenuService.menusFor(ConsoleRoles.parse(permissions)),
                TraceIds.resolve(request));
    }
}
