package com.lp.trade.dto;

/**
 * Request body of {@code POST /api/v1/orders/purchase-ticket}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PurchaseTicketRequest {

    private Long courseId;

    /**
     * Returns the course to purchase.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course to purchase.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
