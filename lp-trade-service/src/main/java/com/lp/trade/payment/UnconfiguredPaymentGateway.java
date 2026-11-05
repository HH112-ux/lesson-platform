package com.lp.trade.payment;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import org.springframework.stereotype.Service;

/**
 * Placeholder {@link PaymentGateway} used until channel credentials exist.
 *
 * <p>It never returns a payable payload, so a client can never start a payment that the server
 * cannot verify. Replace this bean together with the Alipay and WeChat Pay adapters.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
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
}
