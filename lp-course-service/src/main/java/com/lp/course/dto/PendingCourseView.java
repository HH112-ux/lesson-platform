package com.lp.course.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One course waiting for the operations review queue.
 *
 * <p>Mutable because MyBatis fills the columns directly; the service converts the yuan price to fen
 * before returning it to the console.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PendingCourseView {

    private long courseId;

    private String title;

    private String coverUrl;

    private long ownerTeacherId;

    private BigDecimal price;

    private LocalDateTime submittedAt;

    /**
     * Returns the course id.
     *
     * @return course primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(long courseId) {
        this.courseId = courseId;
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
     * Returns the cover URL.
     *
     * @return cover address
     * @author Caspian
     * @since 0.1.0
     */
    public String getCoverUrl() {
        return coverUrl;
    }

    /**
     * Sets the cover URL.
     *
     * @param coverUrl cover address
     * @author Caspian
     * @since 0.1.0
     */
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    /**
     * Returns the teacher that owns the course.
     *
     * @return {@code teacher_profile.id}
     * @author Caspian
     * @since 0.1.0
     */
    public long getOwnerTeacherId() {
        return ownerTeacherId;
    }

    /**
     * Sets the owning teacher.
     *
     * @param ownerTeacherId {@code teacher_profile.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setOwnerTeacherId(long ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    /**
     * Returns the current price in yuan.
     *
     * @return price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Sets the current price in yuan.
     *
     * @param price price in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * Returns the submit time.
     *
     * @return time the teacher submitted the course
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Sets the submit time.
     *
     * @param submittedAt time the teacher submitted the course
     * @author Caspian
     * @since 0.1.0
     */
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
