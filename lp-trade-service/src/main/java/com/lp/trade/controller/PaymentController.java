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
import com.lp.trade.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment initiation and channel callback endpoints.
 *
 * <p>Callbacks respond with the channel specific acknowledgement only after the service applied the
 * notification. While signature verification is unconfigured, every callback is rejected with
 * {@code 50205} and acknowledged as a failure, never as success.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);

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
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(outcome.value());
    }

    /**
     * Alipay asynchronous notification endpoint.
     *
     * @param params form parameters exactly as sent by Alipay
     * @param headers HTTP headers
     * @return plain {@code success} only after the notification was applied, otherwise {@code
     *     failure} so Alipay retries
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/alipay/notify")
    public String alipayNotify(
            @RequestParam Map<String, String> params, @RequestHeader Map<String, String> headers) {
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setOrderNo(params.get("out_trade_no"));
        notification.setChannelTradeNo(params.get("trade_no"));
        notification.setAmount(parseAmount(params.get("total_amount")));
        try {
            paymentService.handleNotify(
                    PaymentChannel.ALIPAY, headers, canonicalForm(params), notification);
            return "success";
        } catch (BizException exception) {
            LOG.warn("alipay notify rejected: code={}", exception.getCode());
            return "failure";
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String canonicalForm(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
