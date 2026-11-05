package com.lp.trade.payment;

import com.lp.trade.dto.PaymentNotification;
import java.util.Map;

/**
 * Verifies channel callback signatures before any state change.
 *
 * <p>This is a port so the payment service has no provider dependency. The placeholder
 * implementation always fails with {@code 50205}; a real implementation is added together with the
 * channel credentials and must verify the raw body, not the parsed fields.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface SignatureVerifier {

    /**
     * Verifies one callback.
     *
     * @param channel payment channel
     * @param headers HTTP headers carrying the channel signature
     * @param rawBody raw request body exactly as received
     * @param notification parsed callback fields
     * @throws com.lp.common.exception.BizException when verification is not configured or fails
     * @author Caspian
     * @since 0.1.0
     */
    void verify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification);
}
