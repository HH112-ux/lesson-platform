package com.lp.trade.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.payment.ChannelTradeState;
import com.lp.trade.payment.MockPaySignatures;
import com.lp.trade.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback endpoint of the local mock payment provider.
 *
 * <p>The provider signs {@code payNo}, {@code orderNo} and {@code amount}, then redirects the
 * browser here. The endpoint rebuilds the canonical payload and hands it to the ordinary payment
 * callback path, so signature verification, idempotency, amount checks and entitlement granting are
 * the same code the real channel uses. Nothing is marked paid before verification passes.
 *
 * @author Caspian
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/payments/mock")
@ConditionalOnProperty(prefix = "lp.pay.mock", name = "enabled", havingValue = "true")
public class MockPaymentController {

    private final PaymentService paymentService;

    /**
     * Creates the controller with its payment service.
     *
     * @param paymentService payment operations
     * @author Caspian
     * @since 0.2.0
     */
    public MockPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Applies a signed local payment notification.
     *
     * @param payNo platform payment number covered by the signature
     * @param orderNo platform order number covered by the signature
     * @param amount paid amount covered by the signature
     * @param signature HMAC of the canonical payload
     * @param request current request, used to resolve the trace id
     * @return success envelope once the notification was verified and applied
     * @author Caspian
     * @since 0.2.0
     */
    @GetMapping("/callback")
    public Result<String> callback(
            @RequestParam String payNo,
            @RequestParam String orderNo,
            @RequestParam String amount,
            @RequestParam String signature,
            HttpServletRequest request) {
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setOrderNo(orderNo);
        notification.setChannelTradeNo(payNo);
        notification.setAmount(new BigDecimal(amount));
        notification.setTradeStatus(ChannelTradeState.TRADE_SUCCESS);
        String canonical = MockPaySignatures.canonical(payNo, orderNo, amount);
        paymentService.handleNotify(
                PaymentChannel.ALIPAY,
                Map.of(MockPaySignatures.SIGNATURE_HEADER, signature),
                canonical,
                notification);
        return Result.ok("MOCK PAY OK", TraceIds.resolve(request));
    }
}
