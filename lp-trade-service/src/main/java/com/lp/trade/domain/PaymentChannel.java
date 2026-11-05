package com.lp.trade.domain;

/**
 * Payment channel values stored in {@code trade_order.channel} and {@code payment_record.channel}.
 *
 * <p>The channel is fixed by the request source platform and never chosen by the client.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class PaymentChannel {

    /** Alipay, used by the web site. */
    public static final int ALIPAY = 1;

    /** WeChat Pay, used by the miniapp. */
    public static final int WXPAY = 2;

    /** Payment scene for a web page payment. */
    public static final int SCENE_WEB = 1;

    /** Payment scene for a miniapp JSAPI payment. */
    public static final int SCENE_MINIAPP = 2;

    private PaymentChannel() {}
}
