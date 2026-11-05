package com.lp.trade.service;

import com.lp.trade.dto.CartView;

/**
 * Cart operations for course products.
 *
 * <p>Quantities do not exist: a course is either in the cart or not.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CartService {

    /**
     * Adds a course to the cart after sale and ownership checks.
     *
     * @param userId authenticated user id
     * @param courseId course id
     * @throws com.lp.common.exception.BizException when the course is missing, not on sale or
     *     already owned
     * @author Caspian
     * @since 0.1.0
     */
    void addItem(long userId, long courseId);

    /**
     * Returns the cart with current course data.
     *
     * @param userId authenticated user id
     * @return cart view, empty when nothing was added
     * @author Caspian
     * @since 0.1.0
     */
    CartView cart(long userId);

    /**
     * Removes a course from the cart; an absent row is treated as success.
     *
     * @param userId authenticated user id
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    void removeItem(long userId, long courseId);
}
