package com.lp.learn.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.learn.dto.GrantEntitlementRequest;
import com.lp.learn.service.EntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service entitlement writes.
 *
 * <p>Routes use the {@code /internal/v1} prefix, which the gateway does not route or whitelist, so
 * they are unreachable from the public edge. Delivery comes from the trade outbox relay and is
 * at-least-once; a repeated grant is accepted as success.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/entitlements")
public class EntitlementInternalController {

    private static final int SOURCE_PURCHASE = 1;

    private final EntitlementService entitlementService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param entitlementService entitlement operations
     * @author Caspian
     * @since 0.1.0
     */
    public EntitlementInternalController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    /**
     * Grants one course to one student; a replay is harmless.
     *
     * @param body grant request
     * @param request current request, used to resolve the trace id
     * @return {@code {granted:true}} after the first or any repeated delivery
     * @throws BizException when a required field is missing
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping
    public Result<Map<String, Boolean>> grant(
            @RequestBody(required = false) GrantEntitlementRequest body,
            HttpServletRequest request) {
        if (body == null || body.getUserId() == null || body.getCourseId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId and courseId are required");
        }
        int source = body.getSource() == null ? SOURCE_PURCHASE : body.getSource();
        entitlementService.grant(body.getUserId(), body.getCourseId(), body.getOrderId(), source);
        return Result.ok(Map.of("granted", true), TraceIds.resolve(request));
    }
}
