package com.lp.trade.dto;

/**
 * Request body of {@code POST /api/v1/orders/purchase-ticket/redeem}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RedeemTicketRequest {

    private String ticket;

    /**
     * Returns the ticket to redeem.
     *
     * @return opaque ticket
     * @author Caspian
     * @since 0.1.0
     */
    public String getTicket() {
        return ticket;
    }

    /**
     * Sets the ticket to redeem.
     *
     * @param ticket opaque ticket
     * @author Caspian
     * @since 0.1.0
     */
    public void setTicket(String ticket) {
        this.ticket = ticket;
    }
}
