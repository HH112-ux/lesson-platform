package com.lp.trade.dto;

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

    private final long totalAmountFen;

    private final long discountAmountFen;

    private final long payAmountFen;

    private final LocalDateTime payExpireAt;

    private final LocalDateTime paidAt;

    private final LocalDateTime createdAt;

    private final String cancelReason;

    private final List<OrderItemView> items;

    private final RefundSummaryView refund;

    /**
     * Creates an order detail projection.
     *
     * @param orderId order primary key
     * @param orderNo business order number
     * @param status order status
     * @param channel payment channel
     * @param totalAmountFen original amount in fen
     * @param discountAmountFen discount in fen
     * @param payAmountFen payable amount in fen
     * @param payExpireAt payment deadline, may be {@code null}
     * @param paidAt payment time, may be {@code null}
     * @param createdAt order creation time
     * @param cancelReason cancel reason, may be {@code null}
     * @param items order lines
     * @param refund latest refund state, {@code null} when never applied
     * @author Caspian
     * @since 0.1.0
     */
    public OrderDetailView(
            long orderId,
            String orderNo,
            int status,
            int channel,
            long totalAmountFen,
            long discountAmountFen,
            long payAmountFen,
            LocalDateTime payExpireAt,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            String cancelReason,
            List<OrderItemView> items,
            RefundSummaryView refund) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.totalAmountFen = totalAmountFen;
        this.discountAmountFen = discountAmountFen;
        this.payAmountFen = payAmountFen;
        this.payExpireAt = payExpireAt;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.cancelReason = cancelReason;
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
     * Returns the original amount.
     *
     * @return amount in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getTotalAmountFen() {
        return totalAmountFen;
    }

    /**
     * Returns the discount amount.
     *
     * @return amount in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getDiscountAmountFen() {
        return discountAmountFen;
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
