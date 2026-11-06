package com.lp.trade.dto;

/**
 * Result of cancelling an order.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CancelOrderResult {

    private final long orderId;

    private final int status;

    /**
     * Creates the result.
     *
     * @param orderId order primary key
     * @param status resulting order status, always 2
     * @author Caspian
     * @since 0.1.0
     */
    public CancelOrderResult(long orderId, int status) {
        this.orderId = orderId;
        this.status = status;
    }

    /**
     * Returns the order id.
     *
     * @return order primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Returns the resulting status.
     *
     * @return 2 for a cancelled order
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }
}
