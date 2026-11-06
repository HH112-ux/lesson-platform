package com.lp.course.dto;

/**
 * Internal course projection consumed by the trade service.
 *
 * <p>Money is expressed in fen so downstream order pricing never works with floating point. The
 * status is always present, which lets the caller reject a non-published course instead of
 * receiving a coarse not-found error.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseInternalView {

    private final long courseId;

    private final String title;

    private final String coverUrl;

    private final long price;

    private final int status;

    private final long teacherId;

    private final long lessonCount;

    private final String instructorName;

    /**
     * Creates a fully populated projection.
     *
     * @param courseId course primary key
     * @param title course title
     * @param coverUrl cover image URL
     * @param price price in fen
     * @param status 0 draft, 1 pending review, 2 published, 3 offline
     * @param teacherId owning teacher id
     * @param lessonCount number of live lessons
     * @param instructorName instructor display name, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public CourseInternalView(
            long courseId,
            String title,
            String coverUrl,
            long price,
            int status,
            long teacherId,
            long lessonCount,
            String instructorName) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.price = price;
        this.status = status;
        this.teacherId = teacherId;
        this.lessonCount = lessonCount;
        this.instructorName = instructorName;
    }

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
     * Returns the instructor display name.
     *
     * @return display name, {@code null} when no profile is linked
     * @author Caspian
     * @since 0.1.0
     */
    public String getInstructorName() {
        return instructorName;
    }
}
