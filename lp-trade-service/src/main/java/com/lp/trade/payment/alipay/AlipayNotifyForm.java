package com.lp.trade.payment.alipay;

import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One Alipay asynchronous notification parsed from the raw request body.
 *
 * <p>The body is decoded once and never rebuilt, so the signature covers exactly what arrived.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class AlipayNotifyForm {

    private final Map<String, String> parameters;

    private AlipayNotifyForm(Map<String, String> parameters) {
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    /**
     * Parses one form encoded body keeping arrival order.
     *
     * @param rawBody body exactly as received
     * @return parsed form
     * @throws IllegalArgumentException when a pair is malformed or a field name repeats
     */
    public static AlipayNotifyForm parse(String rawBody) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawBody != null && !rawBody.isBlank()) {
            for (String pair : rawBody.split("&")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("malformed notification pair");
                }
                String name =
                        URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value =
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                if (parameters.put(name, value) != null) {
                    throw new IllegalArgumentException("repeated notification field");
                }
            }
        }
        return new AlipayNotifyForm(parameters);
    }

    /**
     * Returns the decoded parameters in arrival order.
     *
     * @return read only decoded parameters
     */
    public Map<String, String> parameters() {
        return parameters;
    }

    /**
     * Converts an already verified form into the channel neutral notification.
     *
     * @return notification carrying the order number, channel trade number and amount
     * @throws IllegalArgumentException when {@code total_amount} is not a number
     */
    public PaymentNotification toNotification() {
        String amount = parameters.get("total_amount");
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setOrderNo(parameters.get("out_trade_no"));
        notification.setChannelTradeNo(parameters.get("trade_no"));
        notification.setAmount(amount == null || amount.isBlank() ? null : new BigDecimal(amount));
        return notification;
    }
}
