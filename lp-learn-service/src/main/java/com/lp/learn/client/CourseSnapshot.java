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

    private final int lessonCount;

    /**
     * Creates one projection.
     *
     * @param courseId course id
     * @param title course title
     * @param coverUrl cover image URL
     * @param lessonCount live lesson count, used as the progress denominator
     * @author Caspian
     * @since 0.1.0
     */
    public CourseSnapshot(long courseId, String title, String coverUrl, int lessonCount) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.lessonCount = lessonCount;
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

    /**
     * Returns the live lesson count of the course.
     *
     * @return lesson count, {@code 0} when the course has no visible lesson
     * @author Caspian
     * @since 0.1.0
     */
    public int getLessonCount() {
        return lessonCount;
    }
}
