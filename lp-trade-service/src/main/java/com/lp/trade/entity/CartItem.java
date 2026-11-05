package com.lp.trade.entity;

import java.math.BigDecimal;

/**
 * Cart row read from {@code cart_item}.
 *
 * <p>A course product is never accumulated: the schema has no quantity column and one row per user
 * and course.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CartItem {

    private Long id;

    private Long userId;

    private Long courseId;

    private BigDecimal priceSnapshot;

    /**
     * Returns the row id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the row id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the owner user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the owner user id.
     *
     * @param userId user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
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
     * Returns the price captured when the item was added.
     *
     * @return price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    /**
     * Sets the price captured when the item was added.
     *
     * @param priceSnapshot price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }
}
