package com.lp.trade.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response of {@code POST /api/v1/payments/{orderNo}/pay}.
 *
 * <p>{@code payParams} is only populated after real channel credentials are configured; until then
 * the endpoint fails with {@code 50205} and this object is never returned.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PayView {

    private final String payNo;

    private final int channel;

    private final Map<String, String> payParams;

    /**
     * Creates a fully populated view.
     *
     * @param payNo platform payment number
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @param payParams channel specific parameters
     * @author Caspian
     * @since 0.1.0
     */
    @JsonCreator
    public PayView(
            @JsonProperty("payNo") String payNo,
            @JsonProperty("channel") int channel,
            @JsonProperty("payParams") Map<String, String> payParams) {
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
