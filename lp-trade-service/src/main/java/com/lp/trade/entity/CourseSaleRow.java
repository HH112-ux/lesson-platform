package com.lp.trade.entity;

/**
 * Aggregated sales row of one course.
 *
 * @author Caspian
 * @since 0.2.0
 */
public class CourseSaleRow {

    private Long courseId;

    private String title;

    private Long orderCount;

    /**
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.2.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.2.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the course title snapshot.
     *
     * @return title captured on the order line
     * @author Caspian
     * @since 0.2.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the course title snapshot.
     *
     * @param title course title
     * @author Caspian
     * @since 0.2.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the paid order count.
     *
     * @return number of paid lines for the course
     * @author Caspian
     * @since 0.2.0
     */
    public Long getOrderCount() {
        return orderCount;
    }

    /**
     * Sets the paid order count.
     *
     * @param orderCount number of paid lines
     * @author Caspian
     * @since 0.2.0
     */
    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }
}
