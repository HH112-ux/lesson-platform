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
import com.lp.trade.idempotency.IdempotencyDigests;
import com.lp.trade.idempotency.IdempotencyHeaders;
import com.lp.trade.idempotency.IdempotencyKeys;
import com.lp.trade.idempotency.IdempotencyOutcome;
import com.lp.trade.idempotency.IdempotencyScope;
import com.lp.trade.idempotency.IdempotencyService;
import com.lp.trade.service.OrderService;
import com.lp.trade.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final IdempotencyService idempotencyService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param orderService order operations
     * @param refundService refund operations
     * @param idempotencyService replay and concurrency guard for write endpoints
     * @author Caspian
     * @since 0.1.0
     */
    public OrderController(
            OrderService orderService,
            RefundService refundService,
            IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates an order for one course; a repeated idempotency key returns the original order.
     *
     * <p>The key is read from the {@code Idempotency-Key} header. For one deprecation window the
     * legacy body field is accepted as a fallback, and sending both with different values is
     * rejected instead of guessed.
     *
     * @param userId gateway injected user id, may be absent without a token
     * @param source gateway injected request source that fixes the payment channel
     * @param idempotencyKey header key, may be absent for the legacy body fallback
     * @param body course, idempotency key and optional coupon
     * @param request current request, used to resolve the trace id
     * @param response current response, marked when the body is a replay
     * @return serialized response envelope, written back verbatim on a replay
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping
    public ResponseEntity<String> create(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_SOURCE, required = false) String source,
            @RequestHeader(value = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false)
                    String idempotencyKey,
            @RequestBody(required = false) CreateOrderRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        long uid = requireUser(userId);
        if (body == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body is required");
        }
        String key = IdempotencyKeys.resolve(idempotencyKey, body.getIdempotencyKey());
        // The resolved key is written back so the service keeps a single key source.
        body.setIdempotencyKey(key);
        long courseId = body.getCourseId() == null ? 0L : body.getCourseId();
        IdempotencyOutcome<String> outcome =
                idempotencyService.execute(
                        IdempotencyScope.ORDER_CREATE,
                        uid,
                        key,
                        IdempotencyDigests.order(courseId, body.getCouponId()),
                        TraceIds.resolve(request),
                        () -> orderService.create(uid, source, body));
        markReplayed(response, outcome.replayed());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(outcome.value());
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
     * Applies for a refund of a paid order; a repeated call returns the in-progress request.
     *
     * <p>Applicants must be within seven days of payment and at most twenty percent through the
     * course, otherwise the call fails with {@code 62007} and creates no refund row. An admitted
     * request waits for operations; this endpoint never moves money.
     *
     * @param userId gateway injected user id
     * @param idempotencyKey required header key
     * @param orderId order primary key
     * @param body required reason and optional full amount
     * @param request current request, used to resolve the trace id
     * @param response current response, marked when the body is a replay
     * @return serialized response envelope, written back verbatim on a replay
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<String> refund(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false)
                    String idempotencyKey,
            @PathVariable("orderId") long orderId,
            @RequestBody(required = false) ApplyRefundRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        long uid = requireUser(userId);
        String key = IdempotencyKeys.require(idempotencyKey);
        IdempotencyOutcome<String> outcome =
                idempotencyService.execute(
                        IdempotencyScope.REFUND_APPLY,
                        uid,
                        key,
                        IdempotencyDigests.refund(
                                orderId,
                                body == null ? null : body.getAmountFen(),
                                body == null ? null : body.getReason()),
                        TraceIds.resolve(request),
                        () -> refundService.apply(uid, orderId, body));
        markReplayed(response, outcome.replayed());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(outcome.value());
    }

    private void markReplayed(HttpServletResponse response, boolean replayed) {
        if (replayed) {
            response.setHeader(IdempotencyHeaders.REPLAYED, "true");
        }
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
