package com.lp.trade.dto;

/**
 * Body of {@code POST /api/v1/cart/items}.
 *
 * <p>A course product has no quantity: adding the same course twice keeps one row.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class AddCartItemRequest {

    private Long courseId;

    /**
     * Returns the course to add.
     *
     * @return course id, {@code null} when the client omitted it
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course to add.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
