package com.lp.trade.dto;

/**
 * Response body of {@code POST /api/v1/cart/items}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CartAddResult {

    private final boolean added;

    /**
     * Creates the result.
     *
     * @param added always {@code true} when the call succeeds
     * @author Caspian
     * @since 0.1.0
     */
    public CartAddResult(boolean added) {
        this.added = added;
    }

    /**
     * Tells whether the course is in the cart.
     *
     * @return {@code true} after a successful or idempotent add
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isAdded() {
        return added;
    }
}
