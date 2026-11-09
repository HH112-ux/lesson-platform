package com.lp.trade.dto;

/**
 * Buyer supplied refund input.
 *
 * <p>The first release supports full refunds only: when {@code amountFen} is present it must equal
 * the order paid amount, which also rejects an attempt to refund more than was paid. Omitting it
 * means "refund everything".
 *
 * @author Caspian
 * @since 0.1.0
 */
public class ApplyRefundRequest {

    private Long amountFen;

    private String reason;

    /**
     * Returns the requested amount.
     *
     * @return amount in fen, {@code null} means full refund
     * @author Caspian
     * @since 0.1.0
     */
    public Long getAmountFen() {
        return amountFen;
    }

    /**
     * Sets the requested amount.
     *
     * @param amountFen amount in fen, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    /**
     * Returns the reason.
     *
     * @return reason text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the reason.
     *
     * @param reason reason text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
}
