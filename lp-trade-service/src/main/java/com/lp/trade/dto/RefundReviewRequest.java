package com.lp.trade.dto;

/**
 * Operations decision on a refund request.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RefundReviewRequest {

    private Boolean approved;

    private String rejectReason;

    private Long reviewerId;

    private Integer operatorRole;

    /**
     * Returns whether the request is approved.
     *
     * @return {@code true} to approve, {@code false} to reject
     * @author Caspian
     * @since 0.1.0
     */
    public Boolean getApproved() {
        return approved;
    }

    /**
     * Sets whether the request is approved.
     *
     * @param approved {@code true} to approve, {@code false} to reject
     * @author Caspian
     * @since 0.1.0
     */
    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    /**
     * Returns the rejection reason.
     *
     * @return reason text, required when rejecting
     * @author Caspian
     * @since 0.1.0
     */
    public String getRejectReason() {
        return rejectReason;
    }

    /**
     * Sets the rejection reason.
     *
     * @param rejectReason reason text, required when rejecting
     * @author Caspian
     * @since 0.1.0
     */
    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    /**
     * Returns the reviewer user id.
     *
     * @return operations user id injected by the admin service
     * @author Caspian
     * @since 0.1.0
     */
    public Long getReviewerId() {
        return reviewerId;
    }

    /**
     * Sets the reviewer user id.
     *
     * @param reviewerId operations user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    /**
     * Returns the operator role code forwarded by the admin service.
     *
     * @return 0 operations, 1 administrator
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getOperatorRole() {
        return operatorRole;
    }

    /**
     * Sets the operator role code.
     *
     * @param operatorRole 0 operations, 1 administrator
     * @author Caspian
     * @since 0.6.0
     */
    public void setOperatorRole(Integer operatorRole) {
        this.operatorRole = operatorRole;
    }
}
