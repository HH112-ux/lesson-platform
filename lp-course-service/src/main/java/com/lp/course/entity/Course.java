package com.lp.course.entity;

import java.math.BigDecimal;

/**
 * Course row read from the {@code course} table.
 *
 * <p>Only the columns exposed by the internal contract are projected; publishing fields stay in the
 * course-service write paths. {@code lessonCount} comes from a correlated subquery instead of a
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
}
