package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row read from and written to {@code payment_record}.
 *
 * <p>One order can have several payment attempts, but only one can be successful. The unique key
 * {@code uk_channel_trade(channel, channel_trade_no)} is the final guard against a repeated
 * callback creating a second success row.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PaymentRecord {

    private Long id;

    private Long orderId;

    private String payNo;

    private Integer channel;

    private Integer payScene;

    private BigDecimal amount;

    private Integer status;

    private String channelTradeNo;

    private LocalDateTime paidAt;

    /**
     * Returns the primary key.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the primary key.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the owning order id.
     *
     * @return order id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Sets the owning order id.
     *
     * @param orderId order id
     * @author Caspian
     * @since 0.1.0
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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
     * Sets the platform payment number.
     *
     * @param payNo payment number
     * @author Caspian
     * @since 0.1.0
     */
    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    /**
     * Returns the payment channel.
     *
     * @return channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the payment channel.
     *
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /**
     * Returns the payment scene.
     *
     * @return scene from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getPayScene() {
        return payScene;
    }

    /**
     * Sets the payment scene.
     *
     * @param payScene scene from {@link com.lp.trade.domain.PaymentChannel}
     * @author Caspian
     * @since 0.1.0
     */
    public void setPayScene(Integer payScene) {
        this.payScene = payScene;
    }

    /**
     * Returns the amount fixed when the payment was created.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount fixed when the payment was created.
     *
     * @param amount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns the payment status.
     *
     * @return status from {@link com.lp.trade.domain.PaymentStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the payment status.
     *
     * @param status status from {@link com.lp.trade.domain.PaymentStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the channel trade number.
     *
     * @return trade number, {@code null} before a successful callback
     * @author Caspian
     * @since 0.1.0
     */
    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    /**
     * Sets the channel trade number.
     *
     * @param channelTradeNo trade number
     * @author Caspian
     * @since 0.1.0
     */
    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    /**
     * Returns the channel payment time.
     *
     * @return payment time, {@code null} before success
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    /**
     * Sets the channel payment time.
     *
     * @param paidAt payment time
     * @author Caspian
     * @since 0.1.0
     */
    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
