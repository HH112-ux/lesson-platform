package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderView;
import com.lp.trade.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order endpoints.
 *
 * <p>The buyer id and the payment channel both come from gateway injected headers; the body never
 * carries a user id or a channel, so a client cannot pay through the other channel.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param orderService order operations
     * @author Caspian
     * @since 0.1.0
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates an order for one course; a repeated idempotency key returns the original order.
     *
     * @param userId gateway injected user id, may be absent without a token
     * @param source gateway injected request source that fixes the payment channel
     * @param body course, idempotency key and optional coupon
     * @param request current request, used to resolve the trace id
     * @return created or replayed order
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping
    public Result<OrderView> create(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_SOURCE, required = false) String source,
            @RequestBody(required = false) CreateOrderRequest body,
            HttpServletRequest request) {
        return Result.ok(
                orderService.create(requireUser(userId), source, body), TraceIds.resolve(request));
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
