package com.lp.course.dto;

/**
 * Body of the internal review endpoint.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseReviewRequest {

    private boolean approved;

    private Long priceFen;

    private String remark;

    private Long reviewerId;

    private Integer operatorRole;

    /**
     * Returns whether the course is approved.
     *
     * @return {@code true} to publish, {@code false} to send back to draft
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * Sets the decision.
     *
     * @param approved {@code true} to publish, {@code false} to send back to draft
     * @author Caspian
     * @since 0.1.0
     */
    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    /**
     * Returns the price to apply on approval.
     *
     * @return price in fen, {@code null} keeps the current price
     * @author Caspian
     * @since 0.1.0
     */
    public Long getPriceFen() {
        return priceFen;
    }

    /**
     * Sets the price to apply on approval.
     *
     * @param priceFen price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public void setPriceFen(Long priceFen) {
        this.priceFen = priceFen;
    }

    /**
     * Returns the rejection remark.
     *
     * @return remark text, required when rejecting
     * @author Caspian
     * @since 0.1.0
     */
    public String getRemark() {
        return remark;
    }

    /**
     * Sets the rejection remark.
     *
     * @param remark remark text
     * @author Caspian
     * @since 0.1.0
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * Returns the operator user id forwarded by admin-service.
     *
     * @return operator user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getReviewerId() {
        return reviewerId;
    }

    /**
     * Sets the operator user id.
     *
     * @param reviewerId operator user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    /**
     * Returns the operator role code forwarded by admin-service.
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
