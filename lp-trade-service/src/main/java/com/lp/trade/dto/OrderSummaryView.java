package com.lp.trade.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One order row of the buyer order list.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class OrderSummaryView {

    private final long orderId;

    private final String orderNo;

    private final int status;

    private final int channel;

    private final java.math.BigDecimal payAmount;

    private final LocalDateTime createdAt;

    private final List<OrderItemView> items;

    /**
     * Creates an order summary.
     *
     * @param orderId order primary key
     * @param orderNo business order number
     * @param status order status
     * @param channel payment channel
     * @param payAmount payable amount in yuan
     * @param createdAt order creation time
     * @param items order lines
     * @author Caspian
     * @since 0.1.0
     */
    public OrderSummaryView(
            long orderId,
            String orderNo,
            int status,
            int channel,
            java.math.BigDecimal payAmount,
            LocalDateTime createdAt,
            List<OrderItemView> items) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.payAmount = payAmount;
        this.createdAt = createdAt;
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
     * Returns the payable amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public java.math.BigDecimal getPayAmount() {
        return payAmount;
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
