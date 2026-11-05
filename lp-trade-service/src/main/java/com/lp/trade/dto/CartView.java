package com.lp.trade.dto;

import java.util.List;

/**
 * Response body of {@code GET /api/v1/cart}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CartView {

    private final List<CartItemView> items;

    /**
     * Creates the cart view.
     *
     * @param items cart lines, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    public CartView(List<CartItemView> items) {
        this.items = items;
    }

    /**
     * Returns the cart lines.
     *
     * @return cart lines, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    public List<CartItemView> getItems() {
        return items;
    }
}
