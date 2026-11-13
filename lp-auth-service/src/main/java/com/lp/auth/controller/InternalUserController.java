package com.lp.auth.controller;

import com.lp.auth.dto.AdminUserPageView;
import com.lp.auth.dto.UserMetricsView;
import com.lp.auth.dto.UserStatsView;
import com.lp.auth.service.UserAdminQueryService;
import com.lp.auth.service.UserStatsService;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service account queries.
 *
 * <p>The {@code /internal/v1} prefix is unreachable from the gateway and is guarded by {@code
 * X-Internal-Token}.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final UserStatsService userStatsService;

    private final UserAdminQueryService userAdminQueryService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param userStatsService account aggregates
     * @param userAdminQueryService console account list
     * @author Caspian
     * @since 0.1.0
     */
    public InternalUserController(
            UserStatsService userStatsService, UserAdminQueryService userAdminQueryService) {
        this.userStatsService = userStatsService;
        this.userAdminQueryService = userAdminQueryService;
    }

    /**
     * Returns the number of live accounts.
     *
     * @param request current request, used to resolve the trace id
     * @return account statistics for the operations dashboard
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/stats")
    public Result<UserStatsView> stats(HttpServletRequest request) {
        return Result.ok(userStatsService.stats(), TraceIds.resolve(request));
    }

    /**
     * Returns account totals of the dashboard window.
     *
     * @param days window length in days, defaults to 30 and is capped at 90
     * @param request current request, used to resolve the trace id
     * @return live account total and accounts registered inside the window
     * @author Caspian
     * @since 0.21.0
     */
    @GetMapping("/metrics")
    public Result<UserMetricsView> metrics(
            @RequestParam(value = "days", defaultValue = "30") int days,
            HttpServletRequest request) {
        return Result.ok(userStatsService.metrics(days), TraceIds.resolve(request));
    }

    /**
     * Lists accounts for the operations console.
     *
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
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                userAdminQueryService.page(keyword, status, pageNum, pageSize),
                TraceIds.resolve(request));
    }
}
