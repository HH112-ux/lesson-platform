package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order row projected for the operations console order list.
 *
 * <p>Amounts stay in yuan because the console renders them directly; only aggregate endpoints
 * convert to fen.
 *
 * @author Caspian
 * @since 0.2.0
 */
public class AdminOrderRow {

    private String orderNo;

    private Long userId;

    private Integer status;

    private BigDecimal payAmount;

    private LocalDateTime createdAt;

    /**
     * Returns the business order number.
     *
     * @return order number
     * @author Caspian
     * @since 0.2.0
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * Sets the business order number.
     *
     * @param orderNo order number
     * @author Caspian
     * @since 0.2.0
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * Returns the buyer id.
     *
     * @return buyer user id
     * @author Caspian
     * @since 0.2.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the buyer id.
     *
     * @param userId buyer user id
     * @author Caspian
     * @since 0.2.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the order status.
     *
     * @return status defined by {@link com.lp.trade.domain.OrderStatus}
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the order status.
     *
     * @param status order status
     * @author Caspian
     * @since 0.2.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the payable amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.2.0
     */
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    /**
     * Sets the payable amount.
     *
     * @param payAmount amount in yuan
     * @author Caspian
     * @since 0.2.0
     */
    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    /**
     * Returns the order creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the order creation time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.2.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
