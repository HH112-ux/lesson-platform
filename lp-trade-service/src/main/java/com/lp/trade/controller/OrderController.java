package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.CancelOrderRequest;
import com.lp.trade.dto.CancelOrderResult;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderDetailView;
import com.lp.trade.dto.OrderPageView;
import com.lp.trade.dto.OrderView;
import com.lp.trade.dto.RefundView;
import com.lp.trade.service.OrderService;
import com.lp.trade.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private final RefundService refundService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param orderService order operations
     * @param refundService refund operations
     * @author Caspian
     * @since 0.1.0
     */
    public OrderController(OrderService orderService, RefundService refundService) {
        this.orderService = orderService;
        this.refundService = refundService;
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

    /**
     * Lists the orders of the current buyer, newest first.
     *
     * @param userId gateway injected user id
     * @param status optional status filter
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return order page
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<OrderPageView> list(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                orderService.list(requireUser(userId), status, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Returns one order of the current buyer.
     *
     * @param userId gateway injected user id
     * @param orderId order primary key
     * @param request current request, used to resolve the trace id
     * @return order detail
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{orderId}")
    public Result<OrderDetailView> detail(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("orderId") long orderId,
            HttpServletRequest request) {
        return Result.ok(
                orderService.detail(requireUser(userId), orderId), TraceIds.resolve(request));
    }

    /**
     * Cancels a pending order of the current buyer.
     *
     * @param userId gateway injected user id
     * @param orderId order primary key
     * @param body optional cancel reason
     * @param request current request, used to resolve the trace id
     * @return resulting status
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{orderId}/cancel")
    public Result<CancelOrderResult> cancel(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("orderId") long orderId,
            @RequestBody(required = false) CancelOrderRequest body,
            HttpServletRequest request) {
        String reason = body == null ? null : body.getReason();
        return Result.ok(
                orderService.cancel(requireUser(userId), orderId, reason),
                TraceIds.resolve(request));
    }

    /**
     * Refunds a paid order of the current buyer; a repeated call returns the original refund.
     *
     * <p>The first release supports full refunds only. When {@code amount} is present it must equal
     * the order paid amount, so an attempt to refund more than was paid fails with {@code 62018}.
     *
     * @param userId gateway injected user id
     * @param orderId order primary key
     * @param body optional amount and reason
     * @param request current request, used to resolve the trace id
     * @return successful refund
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{orderId}/refund")
    public Result<RefundView> refund(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable("orderId") long orderId,
            @RequestBody(required = false) ApplyRefundRequest body,
            HttpServletRequest request) {
        return Result.ok(
                refundService.refund(requireUser(userId), orderId, body),
                TraceIds.resolve(request));
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
