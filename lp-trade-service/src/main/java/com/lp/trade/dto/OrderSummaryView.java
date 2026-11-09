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

    private final long payAmountFen;

    private final LocalDateTime createdAt;

    private final List<OrderItemView> items;

    private final RefundSummaryView refund;

    /**
     * Creates an order summary.
     *
     * @param orderId order primary key
     * @param orderNo business order number
     * @param status order status
     * @param channel payment channel
     * @param payAmountFen payable amount in fen
     * @param createdAt order creation time
     * @param items order lines
     * @param refund latest refund state, {@code null} when never applied
     * @author Caspian
     * @since 0.1.0
     */
    public OrderSummaryView(
            long orderId,
            String orderNo,
            int status,
            int channel,
            long payAmountFen,
            LocalDateTime createdAt,
            List<OrderItemView> items,
            RefundSummaryView refund) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.payAmountFen = payAmountFen;
        this.createdAt = createdAt;
        this.items = items;
        this.refund = refund;
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
     * @return amount in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getPayAmountFen() {
        return payAmountFen;
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

    /**
     * Returns the latest refund state.
     *
     * @return refund state, {@code null} when the buyer never applied
     * @author Caspian
     * @since 0.1.0
     */
    public RefundSummaryView getRefund() {
        return refund;
    }
}
