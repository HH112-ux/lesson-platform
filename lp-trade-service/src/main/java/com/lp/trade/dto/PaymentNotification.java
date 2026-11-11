package com.lp.trade.dto;

import java.math.BigDecimal;

/**
 * Normalized channel callback consumed by the payment service.
 *
 * <p>Channel specific parsing, decryption and signature verification happen before this object is
 * built, so the service can treat both channels identically.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PaymentNotification {

    private String orderNo;

    private int channel;

    private String channelTradeNo;

    private BigDecimal amount;

    private String tradeStatus;

    /**
     * Returns the platform order number.
     *
     * @return order number
     * @author Caspian
     * @since 0.1.0
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Sets the platform order number.
     *
     * @param orderNo order number
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * Returns the callback channel.
     *
     * @return channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public int getChannel() {
        return channel;
    }

    /**
     * Sets the callback channel.
     *
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannel(int channel) {
        this.channel = channel;
    }

    /**
     * Returns the channel trade number.
     *
     * @return channel trade number
     * @author Caspian
     * @since 0.1.0
     */
    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    /**
     * Sets the channel trade number.
     *
     * @param channelTradeNo channel trade number
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    /**
     * Returns the paid amount reported by the channel.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the paid amount reported by the channel.
     *
     * @param amount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns the raw channel trade status.
     *
     * @return status such as {@code TRADE_SUCCESS}, {@code null} when the channel reports none
     */
    public String getTradeStatus() {
        return tradeStatus;
    }

    /**
     * Sets the raw channel trade status.
     *
     * @param tradeStatus status as sent by the channel
     */
    public void setTradeStatus(String tradeStatus) {
        this.tradeStatus = tradeStatus;
    }
}
