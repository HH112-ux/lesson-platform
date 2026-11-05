package com.lp.learn.client;

/**
 * Course projection read from course-service.
 *
 * <p>The learn service only needs display data; pricing and sale state stay in trade-service.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseSnapshot {

    private final long courseId;

    private final String title;

    private final String coverUrl;

    /**
     * Creates one projection.
     *
     * @param courseId course id
     * @param title course title
     * @param coverUrl cover image URL
     * @author Caspian
     * @since 0.1.0
     */
    public CourseSnapshot(long courseId, String title, String coverUrl) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
    }

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
     * Returns the cover image URL.
     *
     * @return cover URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getCoverUrl() {
        return coverUrl;
    }
}
