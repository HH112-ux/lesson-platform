package com.lp.course.dto;

/**
 * Public course projection used by the web purchase page.
 *
 * <p>Only fields a buyer may see are exposed; teacher ownership, review data and internal flags
 * stay out of the payload. Prices are in fen, matching the order API. The status is always present
 * so the page can tell an offline course apart from a missing one.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PublicCourseView {

    private final long courseId;

    private final String title;

    private final String coverUrl;

    private final long price;

    private final int status;

    private final long lessonCount;

    /**
     * Creates a fully populated projection.
     *
     * @param courseId course primary key
     * @param title course title
     * @param coverUrl cover image URL
     * @param price price in fen
     * @param status 0 draft, 1 pending review, 2 published, 3 offline
     * @param lessonCount number of live lessons
     * @author Caspian
     * @since 0.1.0
     */
    public PublicCourseView(
            long courseId,
            String title,
            String coverUrl,
            long price,
            int status,
            long lessonCount) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.price = price;
        this.status = status;
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
