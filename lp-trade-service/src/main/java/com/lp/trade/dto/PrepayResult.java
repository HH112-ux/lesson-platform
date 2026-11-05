package com.lp.trade.dto;

import java.util.Map;

/**
 * Result of a successful prepay request.
 *
 * <p>An unconfigured deployment never produces this object: the gateway throws {@code 50205}
 * instead, so a client can never mistake a placeholder for a payable payload.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PrepayResult {

    private final String payNo;

    private final int channel;

    private final Map<String, String> payParams;

    /**
     * Creates a fully populated result.
     *
     * @param payNo platform payment number
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @param payParams channel specific parameters for the client SDK
     * @author Caspian
     * @since 0.1.0
     */
    public PrepayResult(String payNo, int channel, Map<String, String> payParams) {
        this.payNo = payNo;
        this.channel = channel;
        this.payParams = payParams;
    }

    /**
     * Returns the platform payment number.
     *
     * @return payment number
     * @author Caspian
     * @since 0.1.0
     */
    public String getPayNo() {
        return payNo;
    }

    /**
     * Returns the payment channel.
     *
     * @return channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public int getChannel() {
        return channel;
    }

    /**
     * Returns the channel specific parameters.
     *
     * @return immutable parameter map
     * @author Caspian
     * @since 0.1.0
     */
    public Map<String, String> getPayParams() {
        return payParams;
    }
}
