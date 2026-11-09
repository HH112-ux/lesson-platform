package com.lp.trade.dto;

/**
 * One order line shown on the order list and detail pages.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class OrderItemView {

    private final long courseId;

    private final String title;

    private final long priceFen;

    /**
     * Creates an order line projection.
     *
     * @param courseId course id
     * @param title course title snapshot taken at order creation
     * @param priceFen line price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public OrderItemView(long courseId, String title, long priceFen) {
        this.courseId = courseId;
        this.title = title;
        this.priceFen = priceFen;
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
     * Returns the course title snapshot.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the line price.
     *
     * @return price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getPriceFen() {
        return priceFen;
    }
}
