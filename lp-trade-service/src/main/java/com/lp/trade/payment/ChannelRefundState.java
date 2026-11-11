package com.lp.trade.payment;

import java.math.BigDecimal;

/**
 * Read-only refund state reported by a payment channel.
 *
 * <p>A {@code null} {@code refundStatus} means the channel has no refund for the requested out
 * request number. That is not proof that no refund happened, because channel ledgers can lag;
 * callers must never turn it into a retry or a failure.
 *
 * @param refundStatus channel status, {@link #REFUND_SUCCESS} when the refund completed, otherwise
 *     {@code null} or a provider specific value
 * @param refundAmount refunded amount in yuan, {@code null} when the channel did not report it
 * @author Caspian
 * @since 0.6.0
 */
public record ChannelRefundState(String refundStatus, BigDecimal refundAmount) {

    /** Channel status that means the refund moved money. */
    public static final String REFUND_SUCCESS = "REFUND_SUCCESS";

    /**
     * Returns whether the channel confirmed that the refund completed.
     *
     * @return {@code true} when the status is {@link #REFUND_SUCCESS}
     * @author Caspian
     * @since 0.6.0
     */
    public boolean succeeded() {
        return REFUND_SUCCESS.equals(refundStatus);
    }

    /**
     * Returns whether the reported refund amount equals the expected amount.
     *
     * @param expected expected amount in yuan
     * @return {@code true} when both amounts are present and numerically equal
     * @author Caspian
     * @since 0.6.0
     */
    public boolean amountMatches(BigDecimal expected) {
        return refundAmount != null && expected != null && refundAmount.compareTo(expected) == 0;
    }

    /**
     * Creates the state for a refund the channel does not report.
     *
     * @return state with no status and no amount
     * @author Caspian
     * @since 0.6.0
     */
    public static ChannelRefundState notFound() {
        return new ChannelRefundState(null, null);
    }
}
