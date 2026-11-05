package com.lp.trade.dto;

import java.math.BigDecimal;

/**
 * One course in the cart response.
 *
 * <p>Price is the current price in yuan, not the snapshot taken when the item was added, so the
 * cart always matches the course detail page.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CartItemView {

    private final long courseId;

    private final String title;

    private final String coverUrl;

    private final BigDecimal price;

    /**
     * Creates a cart line.
     *
     * @param courseId course id
     * @param title course title
     * @param coverUrl cover image URL
     * @param price current price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public CartItemView(long courseId, String title, String coverUrl, BigDecimal price) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.price = price;
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
     * Returns the course title.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the cover image URL.
     *
     * @return cover URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getCoverUrl() {
        return coverUrl;
    }

    /**
     * Returns the current price in yuan.
     *
     * @return price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPrice() {
        return price;
    }
}
