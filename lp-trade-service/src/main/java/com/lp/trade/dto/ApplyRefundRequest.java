package com.lp.trade.dto;

import java.math.BigDecimal;

/**
 * Buyer supplied refund input.
 *
 * <p>The first release supports full refunds only: when {@code amount} is present it must equal the
 * order paid amount, which also rejects an attempt to refund more than was paid. Omitting it means
 * "refund everything".
 *
 * @author Caspian
 * @since 0.1.0
 */
public class ApplyRefundRequest {

    private BigDecimal amount;

    private String reason;

    /**
     * Returns the requested amount.
     *
     * @return amount in yuan, {@code null} means full refund
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the requested amount.
     *
     * @param amount amount in yuan, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
