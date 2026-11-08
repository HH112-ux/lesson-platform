package com.lp.trade.payment;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.config.MockPayProperties;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Signature verifier of the local mock payment provider.
 *
 * <p>The mock provider shares the HMAC secret with the local signer, so the callback still travels
 * through the ordinary verification path. A missing, tampered or wrongly channelled signature is
 * rejected with {@code 62016}; only a verified callback reaches the payment service.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
@ConditionalOnProperty(prefix = "lp.pay.mock", name = "enabled", havingValue = "true")
public class MockSignatureVerifier implements SignatureVerifier {

    private final MockPayProperties properties;

    /**
     * Creates the verifier with the mock provider settings.
     *
     * @param properties mock provider settings
     * @throws IllegalStateException when the mock channel is enabled without a secret
     * @author Caspian
     * @since 0.2.0
     */
    public MockSignatureVerifier(MockPayProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException(
                    "lp.pay.mock.secret is required when the mock payment channel is enabled");
        }
        this.properties = properties;
    }

    /**
     * Verifies the HMAC signature of a local callback.
     *
     * @param channel channel of the callback endpoint; only the mock channel is accepted
     * @param headers HTTP headers carrying {@code X-Mock-Signature}
     * @param rawBody canonical payload signed by the local signer
     * @param notification parsed callback fields
     * @throws BizException with {@code 62016} when the channel or signature is invalid
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public void verify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification) {
        if (channel != PaymentChannel.ALIPAY) {
            throw new BizException(
                    ErrorCode.PAYMENT_CHANNEL_MISMATCH, "mock channel only serves alipay");
        }
        String signature = headers == null ? null : headers.get(MockPaySignatures.SIGNATURE_HEADER);
        if (!MockPaySignatures.matches(properties.getSecret(), rawBody, signature)) {
            throw new BizException(
                    ErrorCode.PAYMENT_SIGNATURE_INVALID, "mock payment signature is invalid");
        }
    }
}
