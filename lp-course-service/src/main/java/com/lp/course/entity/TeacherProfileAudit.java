package com.lp.course.entity;

import java.time.LocalDateTime;

/**
 * Teacher profile row with its onboarding audit fields.
 *
 * <p>The audit columns live on {@code teacher_profile} until the unified audit table arrives in
 * L1-7, so this row is the only place the operations decision, its remark and the status before and
 * after the decision are kept.
 *
 * @author Caspian
 * @since 0.5.0
 */
public class TeacherProfileAudit {

    private Long id;

    private Long userId;

    private String displayName;

    private String avatarUrl;

    private String bio;

    private Integer status;

    private Integer auditStatus;

    private String auditRemark;

    private Integer auditFromStatus;

    private Long auditedBy;

    private LocalDateTime auditedAt;

    private LocalDateTime submittedAt;

    private Long courseCount;

    private LocalDateTime createdAt;

    /**
     * Returns the teacher profile primary key.
     *
     * @return {@code teacher_profile.id}
     * @author Caspian
     * @since 0.5.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the teacher profile primary key.
     *
     * @param id {@code teacher_profile.id}
     * @author Caspian
     * @since 0.5.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the bound account id.
     *
     * @return {@code sys_user.id}, may be {@code null} for seed rows
     * @author Caspian
     * @since 0.5.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the bound account id.
     *
     * @param userId {@code sys_user.id}
     * @author Caspian
     * @since 0.5.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the public teacher name.
     *
     * @return display name
     * @author Caspian
     * @since 0.5.0
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the public teacher name.
     *
     * @param displayName display name
     * @author Caspian
     * @since 0.5.0
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the avatar URL.
     *
     * @return avatar URL, may be {@code null}
     * @author Caspian
     * @since 0.5.0
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * Sets the avatar URL.
     *
     * @param avatarUrl avatar URL
     * @author Caspian
     * @since 0.5.0
     */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    /**
     * Returns the teacher bio.
     *
     * @return bio, may be {@code null}
     * @author Caspian
     * @since 0.5.0
     */
    public String getBio() {
        return bio;
    }

    /**
     * Sets the teacher bio.
     *
     * @param bio bio
     * @author Caspian
     * @since 0.5.0
     */
    public void setBio(String bio) {
        this.bio = bio;
    }

    /**
     * Returns the account enable flag.
     *
     * @return {@code 1} enabled, {@code 0} disabled
     * @author Caspian
     * @since 0.5.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the account enable flag.
     *
     * @param status {@code 1} enabled, {@code 0} disabled
     * @author Caspian
     * @since 0.5.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the onboarding audit status.
     *
     * @return {@code 0} pending, {@code 1} approved, {@code 2} rejected
     * @author Caspian
     * @since 0.5.0
     */
    public Integer getAuditStatus() {
        return auditStatus;
    }

    /**
     * Sets the onboarding audit status.
     *
     * @param auditStatus {@code 0} pending, {@code 1} approved, {@code 2} rejected
     * @author Caspian
     * @since 0.5.0
     */
    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    /**
     * Returns the audit remark.
     *
     * @return remark, required on rejection
     * @author Caspian
     * @since 0.5.0
     */
    public String getAuditRemark() {
        return auditRemark;
    }

    /**
     * Sets the audit remark.
     *
     * @param auditRemark remark
     * @author Caspian
     * @since 0.5.0
     */
    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    /**
     * Returns the status before the last decision.
     *
     * @return previous audit status, {@code null} before any decision
     * @author Caspian
     * @since 0.5.0
     */
    public Integer getAuditFromStatus() {
        return auditFromStatus;
    }

    /**
     * Sets the status before the last decision.
     *
     * @param auditFromStatus previous audit status
     * @author Caspian
     * @since 0.5.0
     */
    public void setAuditFromStatus(Integer auditFromStatus) {
        this.auditFromStatus = auditFromStatus;
    }

    /**
     * Returns the operator user id of the last decision.
     *
     * @return operator user id, {@code null} before any decision
     * @author Caspian
     * @since 0.5.0
     */
    public Long getAuditedBy() {
        return auditedBy;
    }

    /**
     * Sets the operator user id of the last decision.
     *
     * @param auditedBy operator user id
     * @author Caspian
     * @since 0.5.0
     */
    public void setAuditedBy(Long auditedBy) {
        this.auditedBy = auditedBy;
    }

    /**
     * Returns the decision time.
     *
     * @return decision time, {@code null} before any decision
     * @author Caspian
     * @since 0.5.0
     */
    public LocalDateTime getAuditedAt() {
        return auditedAt;
    }

    /**
     * Sets the decision time.
     *
     * @param auditedAt decision time
     * @author Caspian
     * @since 0.5.0
     */
    public void setAuditedAt(LocalDateTime auditedAt) {
        this.auditedAt = auditedAt;
    }

    /**
     * Returns the application time.
     *
     * @return application time, {@code null} when the profile never applied
     * @author Caspian
     * @since 0.5.0
     */
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Sets the application time.
     *
     * @param submittedAt application time
     * @author Caspian
     * @since 0.5.0
     */
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    /**
     * Returns the number of live courses owned by the teacher.
     *
     * @return course count
     * @author Caspian
     * @since 0.5.0
     */
    public Long getCourseCount() {
        return courseCount;
    }

    /**
     * Sets the number of live courses owned by the teacher.
     *
     * @param courseCount course count
     * @author Caspian
     * @since 0.5.0
     */
    public void setCourseCount(Long courseCount) {
        this.courseCount = courseCount;
    }

    /**
     * Returns the profile creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.5.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the profile creation time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.5.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
