package com.lp.trade.entity;

import java.math.BigDecimal;

/**
 * Aggregated refund figures of the dashboard window and of the current backlog.
 *
 * @author Caspian
 * @since 0.21.0
 */
public class RefundMetricsRow {

    private Long appliedCount;

    private Long refundedCount;

    private BigDecimal refundedAmount;

    private Long pendingCount;

    private Long processingCount;

    /**
     * Returns the applications created inside the window.
     *
     * @return application count
     * @author Caspian
     * @since 0.21.0
     */
    public Long getAppliedCount() {
        return appliedCount;
    }

    /**
     * Sets the applications created inside the window.
     *
     * @param appliedCount application count
     * @author Caspian
     * @since 0.21.0
     */
    public void setAppliedCount(Long appliedCount) {
        this.appliedCount = appliedCount;
    }

    /**
     * Returns the refunds settled inside the window.
     *
     * @return settled refund count
     * @author Caspian
     * @since 0.21.0
     */
    public Long getRefundedCount() {
        return refundedCount;
    }

    /**
     * Sets the refunds settled inside the window.
     *
     * @param refundedCount settled refund count
     * @author Caspian
     * @since 0.21.0
     */
    public void setRefundedCount(Long refundedCount) {
        this.refundedCount = refundedCount;
    }

    /**
     * Returns the refunded amount inside the window.
     *
     * @return amount in yuan, may be {@code null}
     * @author Caspian
     * @since 0.21.0
     */
    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    /**
     * Sets the refunded amount inside the window.
     *
     * @param refundedAmount amount in yuan
     * @author Caspian
     * @since 0.21.0
     */
    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    /**
     * Returns the refunds waiting for a review decision.
     *
     * @return pending refund count
     * @author Caspian
     * @since 0.21.0
     */
    public Long getPendingCount() {
        return pendingCount;
    }

    /**
     * Sets the refunds waiting for a review decision.
     *
     * @param pendingCount pending refund count
     * @author Caspian
     * @since 0.21.0
     */
    public void setPendingCount(Long pendingCount) {
        this.pendingCount = pendingCount;
    }

    /**
     * Returns the refunds approved but not settled locally.
     *
     * @return processing refund count
     * @author Caspian
     * @since 0.21.0
     */
    public Long getProcessingCount() {
        return processingCount;
    }

    /**
     * Sets the refunds approved but not settled locally.
     *
     * @param processingCount processing refund count
     * @author Caspian
     * @since 0.21.0
     */
    public void setProcessingCount(Long processingCount) {
        this.processingCount = processingCount;
    }
}
