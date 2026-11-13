package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated order figures of one calendar day.
 *
 * @author Caspian
 * @since 0.21.0
 */
public class DailyOrderMetricRow {

    private LocalDate day;

    private Long orderCount;

    private Long paidOrderCount;

    private BigDecimal revenue;

    /**
     * Returns the calendar day.
     *
     * @return day of the bucket
     * @author Caspian
     * @since 0.21.0
     */
    public LocalDate getDay() {
        return day;
    }

    /**
     * Sets the calendar day.
     *
     * @param day day of the bucket
     * @author Caspian
     * @since 0.21.0
     */
    public void setDay(LocalDate day) {
        this.day = day;
    }

    /**
     * Returns the order count of the day.
     *
     * @return created orders
     * @author Caspian
     * @since 0.21.0
     */
    public Long getOrderCount() {
        return orderCount;
    }

    /**
     * Sets the order count of the day.
     *
     * @param orderCount created orders
     * @author Caspian
     * @since 0.21.0
     */
    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }

    /**
     * Returns the paid order count of the day.
     *
     * @return settled orders
     * @author Caspian
     * @since 0.21.0
     */
    public Long getPaidOrderCount() {
        return paidOrderCount;
    }

    /**
     * Sets the paid order count of the day.
     *
     * @param paidOrderCount settled orders
     * @author Caspian
     * @since 0.21.0
     */
    public void setPaidOrderCount(Long paidOrderCount) {
        this.paidOrderCount = paidOrderCount;
    }

    /**
     * Returns the settled revenue of the day.
     *
     * @return revenue in yuan, may be {@code null}
     * @author Caspian
     * @since 0.21.0
     */
    public BigDecimal getRevenue() {
        return revenue;
    }

    /**
     * Sets the settled revenue of the day.
     *
     * @param revenue revenue in yuan
     * @author Caspian
     * @since 0.21.0
     */
    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
