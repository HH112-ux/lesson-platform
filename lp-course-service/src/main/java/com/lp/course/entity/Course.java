package com.lp.course.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Course row read from the {@code course} table.
 *
 * <p>Each projection selects only the columns it needs, so fields missing from a query stay {@code
 * null}. The teacher console additionally reads the descriptive and review columns below; the
 * internal contract ignores them. {@code lessonCount} comes from a correlated subquery instead of a
 * second round trip.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class Course {

    private Long id;

    private String title;

    private String coverUrl;

    private BigDecimal price;

    private Integer status;

    private Long ownerTeacherId;

    private Long lessonCount;

    private String instructorName;

    private Long categoryId;

    private String subtitle;

    private String description;

    private BigDecimal originalPrice;

    private Integer level;

    private String reviewRemark;

    private LocalDateTime submittedAt;

    private LocalDateTime updatedAt;

    /**
     * Returns the course id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the course id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the course title.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the course title.
     *
     * @param title title text
     * @author Caspian
     * @since 0.1.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the cover image URL.
     *
     * @return cover URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getCoverUrl() {
        return coverUrl;
    }

    /**
     * Sets the cover image URL.
     *
     * @param coverUrl cover URL
     * @author Caspian
     * @since 0.1.0
     */
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    /**
     * Returns the sale price in yuan as stored in the database.
     *
     * @return price in yuan, never {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Sets the sale price in yuan.
     *
     * @param price price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * Returns the publishing status.
     *
     * @return 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the publishing status.
     *
     * @param status 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the teacher owning the course.
     *
     * @return {@code teacher_profile.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getOwnerTeacherId() {
        return ownerTeacherId;
    }

    /**
     * Sets the owning teacher.
     *
     * @param ownerTeacherId {@code teacher_profile.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setOwnerTeacherId(Long ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    /**
     * Returns the number of live lessons.
     *
     * @return lesson count, {@code 0} when the course has none
     * @author Caspian
     * @since 0.1.0
     */
    public Long getLessonCount() {
        return lessonCount;
    }

    /**
     * Sets the number of live lessons.
     *
     * @param lessonCount lesson count
     * @author Caspian
     * @since 0.1.0
     */
    public void setLessonCount(Long lessonCount) {
        this.lessonCount = lessonCount;
    }

    /**
     * Returns the instructor display name resolved from {@code teacher_profile}.
     *
     * @return display name, {@code null} when the profile is absent
     * @author Caspian
     * @since 0.1.0
     */
    public String getInstructorName() {
        return instructorName;
    }

    /**
     * Sets the instructor display name.
     *
     * @param instructorName display name
     * @author Caspian
     * @since 0.1.0
     */
    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    /**
     * Returns the category id.
     *
     * @return {@code course_category.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCategoryId() {
        return categoryId;
    }

    /**
     * Sets the category id.
     *
     * @param categoryId {@code course_category.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * Returns the subtitle.
     *
     * @return subtitle text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getSubtitle() {
        return subtitle;
    }

    /**
     * Sets the subtitle.
     *
     * @param subtitle subtitle text
     * @author Caspian
     * @since 0.1.0
     */
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Returns the long description.
     *
     * @return description text, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the long description.
     *
     * @param description description text
     * @author Caspian
     * @since 0.1.0
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the crossed out reference price.
     *
     * @return price in yuan, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    /**
     * Sets the crossed out reference price.
     *
     * @param originalPrice price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    /**
     * Returns the difficulty level.
     *
     * @return 1 beginner, 2 intermediate, 3 advanced
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * Sets the difficulty level.
     *
     * @param level 1 beginner, 2 intermediate, 3 advanced
     * @author Caspian
     * @since 0.1.0
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * Returns the review remark left by the operations reviewer.
     *
     * @return remark text, {@code null} when never rejected
     * @author Caspian
     * @since 0.1.0
     */
    public String getReviewRemark() {
        return reviewRemark;
    }

    /**
     * Sets the review remark.
     *
     * @param reviewRemark remark text
     * @author Caspian
     * @since 0.1.0
     */
    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    /**
     * Returns the time the teacher submitted the course.
     *
     * @return submit time, {@code null} while never submitted
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Sets the submit time.
     *
     * @param submittedAt submit time
     * @author Caspian
     * @since 0.1.0
     */
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    /**
     * Returns the last modification time.
     *
     * @return update time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last modification time.
     *
     * @param updatedAt update time
     * @author Caspian
     * @since 0.1.0
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
