package com.lp.trade.payment;

import java.math.BigDecimal;

/**
 * Snapshot of one trade at the payment channel, as returned by a trade query.
 *
 * <p>Only the fields needed to converge local state are kept: the raw channel trade status, the
 * channel trade number and the amount the channel reports as paid. A trade the channel never
 * created is represented by {@link #notFound()} instead of {@code null}, so callers branch on a
 * value and never on a missing response.
 *
 * @param tradeStatus raw channel status such as {@code TRADE_SUCCESS}
 * @param channelTradeNo channel trade number, {@code null} when the trade does not exist
 * @param amount amount the channel reports in yuan, {@code null} when the trade does not exist
 * @author Caspian
 * @since 0.5.0
 */
public record ChannelTradeState(String tradeStatus, String channelTradeNo, BigDecimal amount) {

    /** Channel status of a successful payment. */
    public static final String TRADE_SUCCESS = "TRADE_SUCCESS";

    /** Channel status of a finished, non refundable payment. */
    public static final String TRADE_FINISHED = "TRADE_FINISHED";

    /** Channel status of a closed trade that was never paid. */
    public static final String TRADE_CLOSED = "TRADE_CLOSED";

    /**
     * Creates the state of a trade the channel does not know.
     *
     * @return not found state
     * @author Caspian
     * @since 0.5.0
     */
    public static ChannelTradeState notFound() {
        return new ChannelTradeState(null, null, null);
    }

    /**
     * Tells whether the trade is unknown to the channel.
     *
     * @return {@code true} when the channel reported no such trade
     * @author Caspian
     * @since 0.5.0
     */
    public boolean absent() {
        return tradeStatus == null;
    }

    /**
     * Tells whether the channel collected the money.
     *
     * @return {@code true} for {@code TRADE_SUCCESS} and {@code TRADE_FINISHED}
     * @author Caspian
     * @since 0.5.0
     */
    public boolean paid() {
        return TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);
    }

    /**
     * Tells whether the trade is absent or closed, so no further payment can complete.
     *
     * @return {@code true} when closing the order locally is safe
     * @author Caspian
     * @since 0.5.0
     */
    public boolean absentOrClosed() {
        return absent() || TRADE_CLOSED.equals(tradeStatus);
    }
}
