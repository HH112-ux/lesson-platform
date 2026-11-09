package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund state embedded in a student order row.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefundSummaryView {

    private final long refundId;

    private final String refundNo;

    private final int status;

    private final BigDecimal amount;

    private final String reason;

    private final String reviewReason;

    private final String failReason;

    private final LocalDateTime refundedAt;

    /**
     * Creates the summary.
     *
     * @param refundId refund primary key
     * @param refundNo refund number
     * @param status status from {@code RefundStatus}
     * @param amount refunded amount in yuan
     * @param reason buyer supplied reason
     * @param reviewReason operations comment or rejection reason
     * @param failReason channel failure reason
     * @param refundedAt refund time
     * @author Caspian
     * @since 0.1.0
     */
    public RefundSummaryView(
            long refundId,
            String refundNo,
            int status,
            BigDecimal amount,
            String reason,
            String reviewReason,
            String failReason,
            LocalDateTime refundedAt) {
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.reviewReason = reviewReason;
        this.failReason = failReason;
        this.refundedAt = refundedAt;
    }

    /**
     * Returns the refund primary key.
     *
     * @return refund id
     * @author Caspian
     * @since 0.1.0
     */
    public long getRefundId() {
        return refundId;
    }

    /**
     * Returns the refund number.
     *
     * @return refund number
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefundNo() {
        return refundNo;
    }

    /**
     * Returns the refund status.
     *
     * @return status from {@code RefundStatus}
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the refunded amount.
     *
     * @return amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the buyer supplied reason.
     *
     * @return application reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the operations comment.
     *
     * @return review comment or rejection reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getReviewReason() {
        return reviewReason;
    }

    /**
     * Returns the channel failure reason.
     *
     * @return failure reason
     * @author Caspian
     * @since 0.1.0
     */
    public String getFailReason() {
        return failReason;
    }

    /**
     * Returns when the refund succeeded.
     *
     * @return refund time, {@code null} while not successful
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
