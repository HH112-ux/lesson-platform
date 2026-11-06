package com.lp.learn.dto;

/**
 * One row of the "my courses" response.
 *
 * <p>{@code progressPercent} and {@code lastLessonId} stay at their empty values until the progress
 * sub-slice lands; the response shape is already the documented one so clients do not break later.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class MyCourseView {

    private final long courseId;

    private final String title;

    private final String coverUrl;

    private final int progressPercent;

    private final Long lastLessonId;

    private final boolean refunded;

    /**
     * Creates one row.
     *
     * @param courseId course id
     * @param title course title
     * @param coverUrl cover image URL
     * @param progressPercent completion percentage, {@code 0} before progress tracking exists
     * @param lastLessonId last learned lesson id, {@code null} when none
     * @param refunded {@code true} when the order was refunded and access is revoked
     * @author Caspian
     * @since 0.1.0
     */
    public MyCourseView(
            long courseId,
            String title,
            String coverUrl,
            int progressPercent,
            Long lastLessonId,
            boolean refunded) {
        this.courseId = courseId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.progressPercent = progressPercent;
        this.lastLessonId = lastLessonId;
        this.refunded = refunded;
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
     * Returns the completion percentage.
     *
     * @return percentage from 0 to 100
     * @author Caspian
     * @since 0.1.0
     */
    public int getProgressPercent() {
        return progressPercent;
    }

    /**
     * Returns the last learned lesson.
     *
     * @return lesson id or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getLastLessonId() {
        return lastLessonId;
    }

    /**
     * Returns whether the order was refunded and access is revoked.
     *
     * @return {@code true} for a refunded course; progress stays readable but playback is denied
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isRefunded() {
        return refunded;
    }
}
