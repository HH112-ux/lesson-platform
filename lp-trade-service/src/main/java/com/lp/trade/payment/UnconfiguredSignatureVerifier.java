package com.lp.trade.payment;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.dto.PaymentNotification;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Placeholder {@link SignatureVerifier} used until channel credentials exist.
 *
 * <p>It never returns normally: every callback fails with {@code 50205} instead of silently
 * accepting an unsigned notification. This bean must be replaced, not bypassed, when Alipay or
 * WeChat Pay credentials are wired in.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class UnconfiguredSignatureVerifier implements SignatureVerifier {

    /**
     * Always fails because no channel key or certificate is configured.
     *
     * @param channel payment channel
     * @param headers HTTP headers carrying the channel signature
     * @param rawBody raw request body
     * @param notification parsed callback fields
     * @throws BizException always, with {@code 50205}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void verify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification) {
        throw new BizException(
                ErrorCode.PAYMENT_NOT_CONFIGURED, "signature verifier is not configured");
    }
}
