package com.lp.trade.entity;

import java.math.BigDecimal;

/**
 * Aggregated sales row of one course for the dashboard ranking.
 *
 * @author Caspian
 * @since 0.21.0
 */
public class CourseSalesRankRow {

    private Long courseId;

    private String title;

    private Long orderCount;

    private BigDecimal revenue;

    /**
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.21.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.21.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the course title snapshot.
     *
     * @return title captured on the order line
     * @author Caspian
     * @since 0.21.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the course title snapshot.
     *
     * @param title course title
     * @author Caspian
     * @since 0.21.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the paid order line count.
     *
     * @return number of paid lines for the course
     * @author Caspian
     * @since 0.21.0
     */
    public Long getOrderCount() {
        return orderCount;
    }

    /**
     * Sets the paid order line count.
     *
     * @param orderCount number of paid lines
     * @author Caspian
     * @since 0.21.0
     */
    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }

    /**
     * Returns the revenue of the course lines.
     *
     * @return revenue in yuan, may be {@code null}
     * @author Caspian
     * @since 0.21.0
     */
    public BigDecimal getRevenue() {
        return revenue;
    }

    /**
     * Sets the revenue of the course lines.
     *
     * @param revenue revenue in yuan
     * @author Caspian
     * @since 0.21.0
     */
    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
