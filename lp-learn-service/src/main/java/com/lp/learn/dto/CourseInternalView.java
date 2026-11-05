package com.lp.learn.dto;

/**
 * Subset of the course-service internal projection needed by my-courses.
 *
 * <p>Only the display fields are bound; the remaining fields of the internal response are ignored
 * by the JSON mapper.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseInternalView {

    private long courseId;

    private String title;

    private String coverUrl;

    /**
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
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
}
