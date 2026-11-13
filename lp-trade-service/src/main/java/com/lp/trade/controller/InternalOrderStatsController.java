package com.lp.trade.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.AdminOrderPageView;
import com.lp.trade.dto.OrderDashboardView;
import com.lp.trade.dto.OrderMetricsView;
import com.lp.trade.dto.OrderStatsView;
import com.lp.trade.service.OrderAdminQueryService;
import com.lp.trade.service.OrderDashboardService;
import com.lp.trade.service.OrderStatsService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service order queries.
 *
 * <p>The {@code /internal/v1} prefix is unreachable from the gateway and is guarded by {@code
 * X-Internal-Token}.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/orders")
public class InternalOrderStatsController {

    private final OrderStatsService orderStatsService;

    private final OrderAdminQueryService orderAdminQueryService;

    private final OrderDashboardService orderDashboardService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param orderStatsService order aggregates
     * @param orderAdminQueryService console order list and dashboard metrics
     * @param orderDashboardService dashboard trend, refunds and ranking
     * @author Caspian
     * @since 0.1.0
     */
    public InternalOrderStatsController(
            OrderStatsService orderStatsService,
            OrderAdminQueryService orderAdminQueryService,
            OrderDashboardService orderDashboardService) {
        this.orderStatsService = orderStatsService;
        this.orderAdminQueryService = orderAdminQueryService;
        this.orderDashboardService = orderDashboardService;
    }

    /**
     * Returns order counts and paid revenue.
     *
     * @param request current request, used to resolve the trace id
     * @return order statistics for the operations dashboard
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/stats")
    public Result<OrderStatsView> stats(HttpServletRequest request) {
        return Result.ok(orderStatsService.stats(), TraceIds.resolve(request));
    }

    /**
     * Tells whether a course already has a paid order, which locks its lesson deletion.
     *
     * @param courseId course id
     * @param request current request, used to resolve the trace id
     * @return {@code true} when at least one order still grants the course
     * @author Caspian
     * @since 0.15.0
     */
    @GetMapping("/paid-exists")
    public Result<Boolean> paidExists(
            @RequestParam("courseId") long courseId, HttpServletRequest request) {
        return Result.ok(orderStatsService.hasPaidOrder(courseId), TraceIds.resolve(request));
    }

    /**
     * Lists orders for the operations console.
     *
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
        return Result.ok(
                orderAdminQueryService.page(
                        status, channel, orderNo, userId, dateFrom, dateTo, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Returns the dashboard order metrics of the trailing window.
     *
     * @param days window length in days, defaults to 7 and is capped at 90
     * @param request current request, used to resolve the trace id
     * @return revenue, conversion and the best selling courses
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping("/metrics")
    public Result<OrderMetricsView> metrics(
            @RequestParam(value = "days", defaultValue = "7") int days,
            HttpServletRequest request) {
        return Result.ok(orderAdminQueryService.metrics(days), TraceIds.resolve(request));
    }

    /**
     * Returns the dashboard figures of the trailing calendar window.
     *
     * @param days window length in days, defaults to 30 and is capped at 90
     * @param request current request, used to resolve the trace id
     * @return revenue, orders, refunds, daily trend and the course ranking
     * @author Caspian
     * @since 0.21.0
     */
    @GetMapping("/dashboard")
    public Result<OrderDashboardView> dashboard(
            @RequestParam(value = "days", defaultValue = "30") int days,
            HttpServletRequest request) {
        return Result.ok(orderDashboardService.dashboard(days), TraceIds.resolve(request));
    }
}
