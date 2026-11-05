package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order projection returned by {@code POST /api/v1/orders}.
 *
 * <p>A zero payable amount means the order completed immediately and ownership was granted; in that
 * case {@code paidAt} is populated and {@code payExpireAt} is {@code null}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class OrderView {

    private final String orderNo;

    private final int status;

    private final int channel;

    private final BigDecimal totalAmount;

    private final BigDecimal discountAmount;

    private final BigDecimal payAmount;

    private final long courseId;

    private final LocalDateTime payExpireAt;

    private final LocalDateTime paidAt;

    /**
     * Creates a fully populated view.
     *
     * @param orderNo business order number
     * @param status status from {@link com.lp.trade.domain.OrderStatus}
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @param totalAmount original amount in yuan
     * @param discountAmount discount in yuan
     * @param payAmount payable amount in yuan
     * @param courseId course id
     * @param payExpireAt payment deadline, may be {@code null}
     * @param paidAt payment time, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public OrderView(
            String orderNo,
            int status,
            int channel,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal payAmount,
            long courseId,
            LocalDateTime payExpireAt,
            LocalDateTime paidAt) {
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.payAmount = payAmount;
        this.courseId = courseId;
        this.payExpireAt = payExpireAt;
        this.paidAt = paidAt;
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
     * @return status from {@link com.lp.trade.domain.OrderStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the fixed payment channel.
     *
     * @return channel from {@link com.lp.trade.domain.PaymentChannel}
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
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Returns the payment deadline.
     *
     * @return deadline, {@code null} for an immediately completed order
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPayExpireAt() {
        return payExpireAt;
    }

    /**
     * Returns the payment time.
     *
     * @return payment time, {@code null} while unpaid
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
