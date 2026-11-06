package com.lp.learn.dto;

import java.time.LocalDateTime;

/**
 * Progress of one lesson.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LessonProgressView {

    private final long lessonId;

    private final int positionSeconds;

    private final int maxPositionSeconds;

    private final int watchedSeconds;

    private final boolean completed;

    private final boolean truncated;

    private final LocalDateTime updatedAt;

    /**
     * Creates one lesson progress.
     *
     * @param lessonId lesson id
     * @param positionSeconds latest position in seconds
     * @param maxPositionSeconds historical maximum in seconds
     * @param watchedSeconds accumulated credible watch time in seconds
     * @param completed whether the server marked it complete
     * @param truncated whether this report's increment was capped by the trusted limit
     * @param updatedAt last update time
     * @author Caspian
     * @since 0.1.0
     */
    public LessonProgressView(
            long lessonId,
            int positionSeconds,
            int maxPositionSeconds,
            int watchedSeconds,
            boolean completed,
            boolean truncated,
            LocalDateTime updatedAt) {
        this.lessonId = lessonId;
        this.positionSeconds = positionSeconds;
        this.maxPositionSeconds = maxPositionSeconds;
        this.watchedSeconds = watchedSeconds;
        this.completed = completed;
        this.truncated = truncated;
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the lesson id.
     *
     * @return lesson id
     * @author Caspian
     * @since 0.1.0
     */
    public long getLessonId() {
        return lessonId;
    }

    /**
     * Returns the latest position.
     *
     * @return position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getPositionSeconds() {
        return positionSeconds;
    }

    /**
     * Returns the historical maximum position.
     *
     * @return max position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getMaxPositionSeconds() {
        return maxPositionSeconds;
    }

    /**
     * Returns the accumulated credible watch time.
     *
     * @return watched seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getWatchedSeconds() {
        return watchedSeconds;
    }

    /**
     * Tells whether the lesson is complete.
     *
     * @return {@code true} when complete
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Tells whether this report was capped by the trusted increment limit.
     *
     * @return {@code true} when the increment was truncated
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Returns the last update time.
     *
     * @return update time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
