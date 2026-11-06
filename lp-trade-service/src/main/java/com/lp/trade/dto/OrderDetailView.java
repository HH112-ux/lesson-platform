package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full order detail returned to its owner.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class OrderDetailView {

    private final long orderId;

    private final String orderNo;

    private final int status;

    private final int channel;

    private final BigDecimal totalAmount;

    private final BigDecimal discountAmount;

    private final BigDecimal payAmount;

    private final LocalDateTime payExpireAt;

    private final LocalDateTime paidAt;

    private final LocalDateTime createdAt;

    private final String cancelReason;

    private final List<OrderItemView> items;

    /**
     * Creates an order detail projection.
     *
     * @param orderId order primary key
     * @param orderNo business order number
     * @param status order status
     * @param channel payment channel
     * @param totalAmount original amount in yuan
     * @param discountAmount discount in yuan
     * @param payAmount payable amount in yuan
     * @param payExpireAt payment deadline, may be {@code null}
     * @param paidAt payment time, may be {@code null}
     * @param createdAt order creation time
     * @param cancelReason cancel reason, may be {@code null}
     * @param items order lines
     * @author Caspian
     * @since 0.1.0
     */
    public OrderDetailView(
            long orderId,
            String orderNo,
            int status,
            int channel,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal payAmount,
            LocalDateTime payExpireAt,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            String cancelReason,
            List<OrderItemView> items) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.payAmount = payAmount;
        this.payExpireAt = payExpireAt;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.cancelReason = cancelReason;
        this.items = items;
    }

    /**
     * Returns the order id.
     *
     * @return order primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Returns the business order number.
     *
     * @return order number
     * @author Caspian
     * @since 0.1.0
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Returns the order status.
     *
     * @return 0 pending, 1 paid, 2 cancelled, 3 closed, 4 refunding, 5 refunded
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the payment channel.
     *
     * @return 1 alipay, 2 historical wxpay record
     * @author Caspian
     * @since 0.1.0
     */
    public int getChannel() {
        return channel;
    }

    /**
     * Returns the original amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the discount amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    /**
     * Returns the payable amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    /**
     * Returns the payment deadline.
     *
     * @return deadline, {@code null} when already paid
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPayExpireAt() {
        return payExpireAt;
    }

    /**
     * Returns the payment time.
     *
     * @return payment time, {@code null} when unpaid
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    /**
     * Returns the creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the cancel reason.
     *
     * @return reason text, {@code null} when not cancelled
     * @author Caspian
     * @since 0.1.0
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Returns the order lines.
     *
     * @return order lines
     * @author Caspian
     * @since 0.1.0
     */
    public List<OrderItemView> getItems() {
        return items;
    }
}
