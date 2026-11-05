package com.lp.trade.dto;

/**
 * Response body of {@code DELETE /api/v1/cart/items/{courseId}}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CartRemoveResult {

    private final boolean removed;

    /**
     * Creates the result.
     *
     * @param removed always {@code true} when the call succeeds, even if the row was absent
     * @author Caspian
     * @since 0.1.0
     */
    public CartRemoveResult(boolean removed) {
        this.removed = removed;
    }

    /**
     * Tells whether the course is no longer in the cart.
     *
     * @return {@code true} after a successful or idempotent removal
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isRemoved() {
        return removed;
    }
}
