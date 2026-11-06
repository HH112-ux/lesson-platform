package com.lp.admin.controller;

import com.lp.admin.dto.AdminMenuView;
import com.lp.admin.service.AdminMenuService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
        requireConsoleRole(roles);
        return Result.ok(adminMenuService.menusFor(parse(permissions)), TraceIds.resolve(request));
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

    /**
     * Splits the permission header into a set.
     *
     * @param permissions comma separated permission codes, may be {@code null}
     * @return permission set, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    private Set<String> parse(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }
}
