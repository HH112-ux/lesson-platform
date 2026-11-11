package com.lp.trade.payment;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Placeholder {@link PaymentGateway} used until channel credentials exist.
 *
 * <p>It never returns a payable payload, so a client can never start a payment that the server
 * cannot verify. Replace this bean together with the Alipay adapter.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
@ConditionalOnProperty(
        prefix = "lp.pay.mock",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnconfiguredPaymentGateway implements PaymentGateway {

    /**
     * Always fails because no channel credentials are configured.
     *
     * @param payment payment attempt
     * @return never returns
     * @throws BizException always, with {@code 50205}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PrepayResult prepay(PaymentRecord payment) {
        throw new BizException(
                ErrorCode.PAYMENT_NOT_CONFIGURED, "payment credentials are not configured");
    }

    /**
     * Refuses to refund because no channel credentials are configured.
     *
     * @param payment successful payment
     * @param refundNo local refund number
     * @param amount amount in yuan
     * @return never returns
     * @throws BizException always, with {@code 50205}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public String refund(PaymentRecord payment, String refundNo, BigDecimal amount) {
        throw new BizException(
                ErrorCode.PAYMENT_NOT_CONFIGURED, "payment credentials are not configured");
    }

    /**
     * Refuses to query because no channel credentials are configured.
     *
     * @param payment payment attempt
     * @return never returns
     * @throws BizException always, with {@code 50205}
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public ChannelTradeState query(PaymentRecord payment) {
        throw new BizException(
                ErrorCode.PAYMENT_NOT_CONFIGURED, "payment credentials are not configured");
    }

    /**
     * Refuses to close because no channel credentials are configured.
     *
     * @param payment pending payment attempt
     * @return never returns
     * @throws BizException always, with {@code 50205}
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public boolean close(PaymentRecord payment) {
        throw new BizException(
                ErrorCode.PAYMENT_NOT_CONFIGURED, "payment credentials are not configured");
    }
}
