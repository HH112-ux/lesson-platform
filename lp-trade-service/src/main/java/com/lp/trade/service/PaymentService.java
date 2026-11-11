package com.lp.trade.service;

import com.lp.trade.dto.PayView;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import java.util.Map;

/**
 * Initiates payments and applies channel callbacks.
 *
 * <p>Callbacks are idempotent by the channel trade number and never grant ownership before the
 * channel signature has been verified.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PaymentService {

    /**
     * Creates or reuses a payment attempt for an order and asks the channel for a prepay payload.
     *
     * @param userId authenticated buyer id
     * @param source trusted request source that fixes the channel
     * @param orderNo business order number
     * @return prepay payload for the client SDK
     * @throws com.lp.common.exception.BizException when the order is missing, not payable, expired,
     *     belongs to another user, or the channel is not configured
     * @author Caspian
     * @since 0.1.0
     */
    PayView initiate(long userId, String source, String orderNo);

    /**
     * Verifies and applies one channel callback.
     *
     * @param channel channel of the callback endpoint
     * @param headers HTTP headers carrying the channel signature
     * @param rawBody raw callback body
     * @param notification parsed callback fields
     * @throws com.lp.common.exception.BizException when verification fails, the channel or amount
     *     does not match the order, or the order is no longer payable
     * @author Caspian
     * @since 0.1.0
     */
    void handleNotify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification);

    /**
     * Applies one channel-confirmed payment to an existing attempt.
     *
     * <p>Shared by the signed callback and by trade-query reconciliation so both paths use the same
     * amount guard, the same conditional status update, the same channel trade number uniqueness
     * and the same ownership grant.
     *
     * @param notification channel fields that confirmed the payment
     * @param order order the attempt belongs to
     * @param payment attempt selected by the caller
     * @return {@code true} when the attempt is successful after the call
     * @author Caspian
     * @since 0.5.0
     */
    boolean applyConfirmedPayment(
            PaymentNotification notification, TradeOrder order, PaymentRecord payment);
}
