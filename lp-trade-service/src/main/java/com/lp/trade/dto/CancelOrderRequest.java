package com.lp.trade.dto;

/**
 * Optional body of the cancel order endpoint.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CancelOrderRequest {

    private String reason;

    /**
     * Returns the buyer supplied reason.
     *
     * @return reason text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the buyer supplied reason.
     *
     * @param reason reason text
     * @author Caspian
     * @since 0.1.0
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
}
