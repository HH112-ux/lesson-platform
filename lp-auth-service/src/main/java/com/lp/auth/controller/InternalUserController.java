package com.lp.auth.controller;

import com.lp.auth.dto.UserStatsView;
import com.lp.auth.service.UserStatsService;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service account aggregates.
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

    /**
     * Creates the controller with its service dependency.
     *
     * @param userStatsService account aggregates
     * @author Caspian
     * @since 0.1.0
     */
    public InternalUserController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
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
}
