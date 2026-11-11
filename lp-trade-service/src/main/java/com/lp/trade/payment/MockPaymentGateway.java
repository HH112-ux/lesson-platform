package com.lp.trade.payment;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.config.MockPayProperties;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development-only payment gateway that hands the client a link to the local mock provider.
 *
 * <p>The link carries a signed canonical payload and points at the mock callback controller, which
 * submits the payload to the normal callback path. Nothing is marked paid here: ownership is only
 * granted after {@code SignatureVerifier} has accepted the signature.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
@ConditionalOnProperty(prefix = "lp.pay.mock", name = "enabled", havingValue = "true")
public class MockPaymentGateway implements PaymentGateway {

    private final MockPayProperties properties;

    private final TradeOrderMapper tradeOrderMapper;

    /**
     * Creates the gateway with its settings and order lookup.
     *
     * @param properties mock provider settings
     * @param tradeOrderMapper order lookup used to embed the order number
     * @author Caspian
     * @since 0.2.0
     */
    public MockPaymentGateway(MockPayProperties properties, TradeOrderMapper tradeOrderMapper) {
        this.properties = properties;
        this.tradeOrderMapper = tradeOrderMapper;
    }

    /**
     * Builds a signed local payment link for the payment attempt.
     *
     * @param payment pending payment record
     * @return prepay payload whose {@code payUrl} completes the payment when opened
     * @throws BizException when the order behind the payment no longer exists
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public PrepayResult prepay(PaymentRecord payment) {
        TradeOrder order = tradeOrderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String payNo = payment.getPayNo();
        String orderNo = order.getOrderNo();
        String amount = payment.getAmount().toPlainString();
        String signature =
                MockPaySignatures.sign(
                        properties.getSecret(),
                        MockPaySignatures.canonical(payNo, orderNo, amount));
        String payUrl =
                properties.getGatewayBaseUrl()
                        + "/api/v1/payments/mock/callback?payNo="
                        + MockPaySignatures.encode(payNo)
                        + "&orderNo="
                        + MockPaySignatures.encode(orderNo)
                        + "&amount="
                        + MockPaySignatures.encode(amount)
                        + "&signature="
                        + MockPaySignatures.encode(signature);
        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("payUrl", payUrl);
        payParams.put("payNo", payNo);
        payParams.put("channel", Integer.toString(PaymentChannel.ALIPAY));
        return new PrepayResult(payNo, PaymentChannel.ALIPAY, payParams);
    }

    /**
     * Accepts the refund immediately, mirroring a synchronous channel acknowledgement.
     *
     * @param payment successful payment
     * @param refundNo local refund number, echoed into the channel reference
     * @param amount amount in yuan
     * @return deterministic mock channel refund number
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public String refund(PaymentRecord payment, String refundNo, BigDecimal amount) {
        return "MOCK-" + refundNo;
    }

    /**
     * Reports that the mock provider never created a trade.
     *
     * <p>The mock channel keeps no trade ledger: the signed callback link is its only state, so a
     * query can never find a trade to reconcile.
     *
     * @param payment payment attempt
     * @return always {@link ChannelTradeState#notFound()}
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public ChannelTradeState query(PaymentRecord payment) {
        return ChannelTradeState.notFound();
    }

    /**
     * Accepts every close because the mock provider holds no trade to close.
     *
     * @param payment pending payment attempt
     * @return always {@code true}
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public boolean close(PaymentRecord payment) {
        return true;
    }

    /**
     * Reports every mock refund as successful, mirroring the synchronous mock refund.
     *
     * @param payment successful payment
     * @param refundNo local refund number
     * @return successful state for the full payment amount
     * @author Caspian
     * @since 0.6.0
     */
    @Override
    public ChannelRefundState refundQuery(PaymentRecord payment, String refundNo) {
        return new ChannelRefundState(ChannelRefundState.REFUND_SUCCESS, payment.getAmount());
    }
}
