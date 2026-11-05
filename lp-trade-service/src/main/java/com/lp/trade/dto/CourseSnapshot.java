package com.lp.trade.dto;

/**
 * Course data returned by the course-service internal API.
 *
 * <p>{@code price} is expressed in fen, matching the internal contract.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseSnapshot {

    private long courseId;

    private String title;

    private String coverUrl;

    private long price;

    private int status;

    private long teacherId;

    private long lessonCount;

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
     * Returns the price in fen.
     *
     * @return price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getPrice() {
        return price;
    }

    /**
     * Sets the price in fen.
     *
     * @param price price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public void setPrice(long price) {
        this.price = price;
    }

    /**
     * Returns the publishing status.
     *
     * @return 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Sets the publishing status.
     *
     * @param status 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Returns the owning teacher id.
     *
     * @return teacher id
     * @author Caspian
     * @since 0.1.0
     */
    public long getTeacherId() {
        return teacherId;
    }

    /**
     * Sets the owning teacher id.
     *
     * @param teacherId teacher id
     * @author Caspian
     * @since 0.1.0
     */
    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    /**
     * Returns the number of live lessons.
     *
     * @return lesson count
     * @author Caspian
     * @since 0.1.0
     */
    public long getLessonCount() {
        return lessonCount;
    }

    /**
     * Sets the number of live lessons.
     *
     * @param lessonCount lesson count
     * @author Caspian
     * @since 0.1.0
     */
    public void setLessonCount(long lessonCount) {
        this.lessonCount = lessonCount;
    }
}
