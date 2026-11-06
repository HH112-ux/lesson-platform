package com.lp.trade.dto;

/**
 * Response body of {@code POST /api/v1/orders/purchase-ticket}.
 *
 * <p>{@code url} and {@code qrPayload} are identical; the miniapp renders the latter as a QR code
 * and the former as a copyable link. The ticket is opaque and carries no user id.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PurchaseTicketView {

    private final String url;

    private final String qrPayload;

    private final String ticket;

    private final String expiresAt;

    /**
     * Creates the response.
     *
     * @param url browser URL containing the ticket
     * @param qrPayload payload to encode in the QR code, same as {@code url}
     * @param ticket opaque one-time ticket
     * @param expiresAt ISO-8601 expiry timestamp
     * @author Caspian
     * @since 0.1.0
     */
    public PurchaseTicketView(String url, String qrPayload, String ticket, String expiresAt) {
        this.url = url;
        this.qrPayload = qrPayload;
        this.ticket = ticket;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the browser URL.
     *
     * @return purchase page URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the QR payload.
     *
     * @return same value as {@link #getUrl()}
     * @author Caspian
     * @since 0.1.0
     */
    public String getQrPayload() {
        return qrPayload;
    }

    /**
     * Returns the opaque ticket.
     *
     * @return one-time ticket
     * @author Caspian
     * @since 0.1.0
     */
    public String getTicket() {
        return ticket;
    }

    /**
     * Returns the expiry timestamp.
     *
     * @return ISO-8601 instant
     * @author Caspian
     * @since 0.1.0
     */
    public String getExpiresAt() {
        return expiresAt;
    }
}
