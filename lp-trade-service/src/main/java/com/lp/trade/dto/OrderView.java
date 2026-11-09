package com.lp.trade.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    private final long totalAmountFen;

    private final long discountAmountFen;

    private final long payAmountFen;

    private final long courseId;

    private final LocalDateTime payExpireAt;

    private final LocalDateTime paidAt;

    /**
     * Creates a fully populated view.
     *
     * @param orderNo business order number
     * @param status status from {@link com.lp.trade.domain.OrderStatus}
     * @param channel channel from {@link com.lp.trade.domain.PaymentChannel}
     * @param totalAmountFen original amount in fen
     * @param discountAmountFen discount in fen
     * @param payAmountFen payable amount in fen
     * @param courseId course id
     * @param payExpireAt payment deadline, may be {@code null}
     * @param paidAt payment time, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    @JsonCreator
    public OrderView(
            @JsonProperty("orderNo") String orderNo,
            @JsonProperty("status") int status,
            @JsonProperty("channel") int channel,
            @JsonProperty("totalAmountFen") long totalAmountFen,
            @JsonProperty("discountAmountFen") long discountAmountFen,
            @JsonProperty("payAmountFen") long payAmountFen,
            @JsonProperty("courseId") long courseId,
            @JsonProperty("payExpireAt") LocalDateTime payExpireAt,
            @JsonProperty("paidAt") LocalDateTime paidAt) {
        this.orderNo = orderNo;
        this.status = status;
        this.channel = channel;
        this.totalAmountFen = totalAmountFen;
        this.discountAmountFen = discountAmountFen;
        this.payAmountFen = payAmountFen;
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
