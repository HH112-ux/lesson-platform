package com.lp.trade.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.OrderStatsView;
import com.lp.trade.service.OrderStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service order aggregates.
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

    /**
     * Creates the controller with its service dependency.
     *
     * @param orderStatsService order aggregates
     * @author Caspian
     * @since 0.1.0
     */
    public InternalOrderStatsController(OrderStatsService orderStatsService) {
        this.orderStatsService = orderStatsService;
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
}
