package com.lp.admin.controller;

import com.lp.admin.dto.AdminOrderPageView;
import com.lp.admin.service.AdminOrderQueryService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations console order list endpoint.
 *
 * <p>The gateway authenticates the caller and injects the roles; only {@code ADMIN} and {@code OPS}
 * may read orders. Data stays in trade-service and is reached over the internal API.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminOrderQueryService order list use case
     * @author Caspian
     * @since 0.2.0
     */
    public AdminOrderController(AdminOrderQueryService adminOrderQueryService) {
        this.adminOrderQueryService = adminOrderQueryService;
    }

    /**
     * Lists orders with status, channel, number, buyer and date filters.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param dateFrom optional inclusive creation date
     * @param dateTo optional inclusive creation date upper bound
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of orders
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping
    public Result<AdminOrderPageView> orders(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "channel", required = false) Integer channel,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "dateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.ORDER_LIST);
        return Result.ok(
                adminOrderQueryService.page(
                        status, channel, orderNo, userId, dateFrom, dateTo, pageNum, pageSize),
                TraceIds.resolve(request));
    }
}
