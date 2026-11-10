package com.lp.course.entity;

import java.time.LocalDateTime;

/**
 * One immutable course review decision written to {@code course_review_audit}.
 *
 * <p>Course rows only keep the latest decision; this table keeps the full history so operations can
 * audit who approved or rejected a course and why.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseReviewAudit {

    private Long id;

    private Long courseId;

    private Integer action;

    private Integer fromStatus;

    private Integer toStatus;

    private Long reviewerId;

    private String remark;

    private LocalDateTime createdAt;

    /**
     * Returns the audit id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the audit id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the reviewed course id.
     *
     * @return {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the reviewed course id.
     *
     * @param courseId {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the decision action.
     *
     * @return 1 approved, 2 rejected, 3 withdrawn by the owning teacher
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getAction() {
        return action;
    }

    /**
     * Sets the decision action.
     *
     * @param action 1 approved, 2 rejected, 3 withdrawn by the owning teacher
     * @author Caspian
     * @since 0.1.0
     */
    public void setAction(Integer action) {
        this.action = action;
    }

    /**
     * Returns the course status before the decision.
     *
     * @return status before the decision, always 1 pending review
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getFromStatus() {
        return fromStatus;
    }

    /**
     * Sets the course status before the decision.
     *
     * @param fromStatus status before the decision
     * @author Caspian
     * @since 0.1.0
     */
    public void setFromStatus(Integer fromStatus) {
        this.fromStatus = fromStatus;
    }

    /**
     * Returns the course status after the decision.
     *
     * @return 2 published on approval, 0 draft on rejection or teacher withdrawal
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getToStatus() {
        return toStatus;
    }

    /**
     * Sets the course status after the decision.
     *
     * @param toStatus 2 published on approval, 0 draft on rejection or teacher withdrawal
     * @author Caspian
     * @since 0.1.0
     */
    public void setToStatus(Integer toStatus) {
        this.toStatus = toStatus;
    }

    /**
     * Returns the reviewer user id.
     *
     * @return operator user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getReviewerId() {
        return reviewerId;
    }

    /**
     * Sets the reviewer user id.
     *
     * @param reviewerId operator user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    /**
     * Returns the review remark.
     *
     * @return remark text, {@code null} on approval
     * @author Caspian
     * @since 0.1.0
     */
    public String getRemark() {
        return remark;
    }

    /**
     * Sets the review remark.
     *
     * @param remark remark text
     * @author Caspian
     * @since 0.1.0
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * Returns the decision time.
     *
     * @return decision time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the decision time.
     *
     * @param createdAt decision time
     * @author Caspian
     * @since 0.1.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
