package com.lp.course.dto;

/**
 * Body of the internal review endpoint.
 *
 * <p>{@code priceFen} is the only amount of the review path and is always expressed in fen.
 * Teachers submit their proposed price in yuan through the teacher course form; the server owns the
 * conversion between the two representations, so no caller or console converts an amount itself.
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
     * Returns the final price to apply on approval.
     *
     * <p>Unit is fen, never yuan. The teacher proposed price is stored in yuan, so this field is
     * the final decision of operations and the server converts it before writing the course row.
     *
     * @return price in fen, {@code null} keeps the current price
     * @author Caspian
     * @since 0.1.0
     */
    public Long getPriceFen() {
        return priceFen;
    }

    /**
     * Sets the final price to apply on approval.
     *
     * @param priceFen price in fen, never yuan
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
