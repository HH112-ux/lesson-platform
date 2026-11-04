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

    private final long price;

    private final int status;

    private final long teacherId;

    private final long lessonCount;

    /**
     * Creates a fully populated projection.
     *
     * @param courseId course primary key
     * @param title course title
     * @param price price in fen
     * @param status 0 draft, 1 pending review, 2 published, 3 offline
     * @param teacherId owning teacher id
     * @param lessonCount number of live lessons
     * @author Caspian
     * @since 0.1.0
     */
    public CourseInternalView(
            long courseId, String title, long price, int status, long teacherId, long lessonCount) {
        this.courseId = courseId;
        this.title = title;
        this.price = price;
        this.status = status;
        this.teacherId = teacherId;
        this.lessonCount = lessonCount;
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
}
