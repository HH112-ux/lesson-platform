package com.lp.trade.payment;

import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;

/**
 * Builds a channel specific prepay request.
 *
 * <p>This is a port so the payment service has no provider dependency. The placeholder
 * implementation always fails with {@code 50205}; a real implementation signs the request with
 * channel credentials and returns the parameters the client SDK needs.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PaymentGateway {

    /**
     * Creates a prepay request at the channel.
     *
     * @param payment payment attempt already persisted locally
     * @return channel parameters for the client SDK
     * @throws com.lp.common.exception.BizException when credentials are missing or the channel
     *     fails
     * @author Caspian
     * @since 0.1.0
     */
    PrepayResult prepay(PaymentRecord payment);
}
