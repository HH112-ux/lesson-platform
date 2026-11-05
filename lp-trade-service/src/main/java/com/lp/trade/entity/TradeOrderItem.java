package com.lp.trade.entity;

import java.math.BigDecimal;

/**
 * Row read from and written to {@code trade_order_item}.
 *
 * <p>The course title and owner teacher are snapshotted at order time so later course edits never
 * rewrite history and the teacher dashboard can aggregate without a cross database join.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class TradeOrderItem {

    private Long id;

    private Long orderId;

    private Long courseId;

    private Long ownerTeacherId;

    private String courseTitle;

    private BigDecimal price;

    private BigDecimal discountAmount;

    private BigDecimal actualAmount;

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
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the snapshotted owner teacher id.
     *
     * @return teacher id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOwnerTeacherId() {
        return ownerTeacherId;
    }

    /**
     * Sets the snapshotted owner teacher id.
     *
     * @param ownerTeacherId teacher id
     * @author Caspian
     * @since 0.1.0
     */
    public void setOwnerTeacherId(Long ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    /**
     * Returns the snapshotted course title.
     *
     * @return course title
     * @author Caspian
     * @since 0.1.0
     */
    public String getCourseTitle() {
        return courseTitle;
    }

    /**
     * Sets the snapshotted course title.
     *
     * @param courseTitle course title
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    /**
     * Returns the original price.
     *
     * @return price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Sets the original price.
     *
     * @param price price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * Returns the discount allocated to this line.
     *
     * @return discount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    /**
     * Sets the discount allocated to this line.
     *
     * @param discountAmount discount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    /**
     * Returns the actual paid amount on this line.
     *
     * @return actual amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    /**
     * Sets the actual paid amount on this line.
     *
     * @param actualAmount actual amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }
}
