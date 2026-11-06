package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.trade.dto.FailedOutboxPage;
import com.lp.trade.entitlement.EntitlementOutboxMetrics;
import com.lp.trade.entitlement.OutboxAdminService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal operations endpoints for the entitlement outbox.
 *
 * <p>These routes live under {@code /internal/v1/**} and are never forwarded by the gateway. They
 * expose given up intents so support can inspect and requeue them instead of editing the database.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/entitlement-outbox")
public class InternalEntitlementOutboxController {

    private final OutboxAdminService outboxAdminService;

    private final EntitlementOutboxMetrics metrics;

    /**
     * Creates the controller with its dependencies.
     *
     * @param outboxAdminService operations queries and retries
     * @param metrics gauge values exposed to operations tooling
     * @author Caspian
     * @since 0.1.0
     */
    public InternalEntitlementOutboxController(
            OutboxAdminService outboxAdminService, EntitlementOutboxMetrics metrics) {
        this.outboxAdminService = outboxAdminService;
        this.metrics = metrics;
    }

    /**
     * Returns the last observed backlog gauges without needing a metrics scrape endpoint.
     *
     * @return {@code {pending, failed}}
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(
                Map.of("pending", metrics.pendingCount(), "failed", metrics.failedCount()));
    }

    /**
     * Lists intents the relay gave up on, newest first.
     *
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of given up intents
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/failed")
    public Result<FailedOutboxPage> failed(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(outboxAdminService.failed(pageNum, pageSize));
    }

    /**
     * Requeues one given up intent so the relay retries it.
     *
     * @param id outbox id
     * @return {@code {requeued:true}}
     * @throws BizException with {@code 40401} when the intent is absent or not given up
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{id}/retry")
    public Result<Map<String, Boolean>> retry(@PathVariable("id") long id) {
        if (!outboxAdminService.retry(id)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok(Map.of("requeued", true));
    }
}
