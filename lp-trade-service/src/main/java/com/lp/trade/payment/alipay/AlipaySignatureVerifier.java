package com.lp.trade.payment.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.payment.SignatureVerifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RSA2 verifier of Alipay asynchronous notifications.
 *
 * <p>A valid signature alone is not enough: {@code app_id} and {@code seller_id} must belong to
 * this application.
 *
 * @author Caspian
 * @since 0.4.0
 */
public class AlipaySignatureVerifier implements SignatureVerifier {

    private static final String SIGN_TYPE = "RSA2";

    private final AlipayCredentials credentials;

    private final String sellerId;

    /**
     * Creates the verifier.
     *
     * @param credentials application id and Alipay public key of this channel
     * @param sellerId merchant seller id every callback must echo
     * @throws IllegalStateException when {@code sellerId} is blank
     */
    public AlipaySignatureVerifier(AlipayCredentials credentials, String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalStateException("lp.pay.alipay.seller-id is required when enabled");
        }
        this.credentials = credentials;
        this.sellerId = sellerId;
    }

    /**
     * Verifies one callback against the raw body and the merchant identity.
     *
     * @param channel channel of the callback; only the Alipay channel is accepted
     * @param headers HTTP headers, unused because Alipay signs the body
     * @param rawBody body exactly as received
     * @param notification parsed fields, ignored by this verifier
     * @throws BizException {@code 62010} for another channel, {@code 62016} on any failed check
     */
    @Override
    public void verify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification) {
        if (channel != PaymentChannel.ALIPAY) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_MISMATCH);
        }
        AlipayNotifyForm form;
        try {
            form = AlipayNotifyForm.parse(rawBody);
        } catch (IllegalArgumentException exception) {
            throw invalid("malformed notification body");
        }
        Map<String, String> parameters = form.parameters();
        require(SIGN_TYPE.equals(parameters.get("sign_type")), "unsupported sign_type");
        require(signatureMatches(parameters), "signature mismatch");
        require(credentials.appId().equals(parameters.get("app_id")), "app_id mismatch");
        require(sellerId.equals(parameters.get("seller_id")), "seller_id mismatch");
    }

    // The SDK removes sign and sign_type from the map it is given, so a copy is passed in.
    private boolean signatureMatches(Map<String, String> parameters) {
        String charset = parameters.get("charset");
        try {
            return AlipaySignature.rsaCheckV1(
                    new LinkedHashMap<>(parameters),
                    credentials.alipayPublicKey(),
                    charset == null || charset.isBlank() ? "UTF-8" : charset,
                    SIGN_TYPE);
        } catch (AlipayApiException exception) {
            return false;
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw invalid(reason);
        }
    }

    private static BizException invalid(String reason) {
        return new BizException(ErrorCode.PAYMENT_SIGNATURE_INVALID, reason);
    }
}
