package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.web.TraceIds;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.idempotency.IdempotencyDigests;
import com.lp.trade.idempotency.IdempotencyHeaders;
import com.lp.trade.idempotency.IdempotencyKeys;
import com.lp.trade.idempotency.IdempotencyOutcome;
import com.lp.trade.idempotency.IdempotencyScope;
import com.lp.trade.idempotency.IdempotencyService;
import com.lp.trade.payment.alipay.AlipayNotifyForm;
import com.lp.trade.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment initiation and channel callback endpoints.
 *
 * <p>Callbacks respond with the channel specific acknowledgement. {@code success} only means the
 * notification was received; an unapplied callback is recorded and still acknowledged, never paid
 * or granted. A disabled Alipay channel rejects every callback with {@code 50205}.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);

    /** Explicit UTF-8 media type, so a replay never depends on the global encoding config. */
    private static final MediaType JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final int MAX_NOTIFY_BODY_LENGTH = 16 * 1024;

    private final PaymentService paymentService;

    private final IdempotencyService idempotencyService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param paymentService payment operations
     * @param idempotencyService replay and concurrency guard for payment initiation
     * @author Caspian
     * @since 0.1.0
     */
    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Starts or resumes a payment for an order.
     *
     * @param userId gateway injected user id
     * @param source gateway injected request source that fixes the channel
     * @param idempotencyKey required header key
     * @param orderNo business order number
     * @param request current request, used to resolve the trace id
     * @param response current response, marked when the body is a replay
     * @return serialized response envelope, written back verbatim on a replay
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{orderNo}/pay")
    public ResponseEntity<String> pay(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_SOURCE, required = false) String source,
            @RequestHeader(value = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false)
                    String idempotencyKey,
            @PathVariable String orderNo,
            HttpServletRequest request,
            HttpServletResponse response) {
        long uid = requireUser(userId);
        String key = IdempotencyKeys.require(idempotencyKey);
        IdempotencyOutcome<String> outcome =
                idempotencyService.execute(
                        IdempotencyScope.PAYMENT_INITIATE,
                        uid,
                        key,
                        IdempotencyDigests.payment(orderNo),
                        TraceIds.resolve(request),
                        () -> paymentService.initiate(uid, source, orderNo));
        if (outcome.replayed()) {
            response.setHeader(IdempotencyHeaders.REPLAYED, "true");
        }
        return ResponseEntity.ok().contentType(JSON_UTF8).body(outcome.value());
    }

    /**
     * Alipay asynchronous notification endpoint.
     *
     * <p>The body is read verbatim and verified exactly as received; rebuilding it from request
     * parameters would change ordering, encoding and empty values. The endpoint needs no token
     * because Alipay cannot carry one, accepts POST only, and rejects oversized bodies with 413.
     *
     * @param rawBody form body exactly as sent by Alipay
     * @param headers HTTP headers
     * @return plain {@code success} unless verification or the order lookup fails; {@code success}
     *     means received, not that the order was paid or granted
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/alipay/notify")
    public String alipayNotify(
            @RequestBody String rawBody, @RequestHeader Map<String, String> headers) {
        if (rawBody.length() > MAX_NOTIFY_BODY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "notification body is too large");
        }
        try {
            PaymentNotification notification = AlipayNotifyForm.parse(rawBody).toNotification();
            paymentService.handleNotify(PaymentChannel.ALIPAY, headers, rawBody, notification);
            return "success";
        } catch (BizException | IllegalArgumentException exception) {
            LOG.warn("alipay notify rejected: {}", exception.getMessage());
            return "failure";
        }
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
