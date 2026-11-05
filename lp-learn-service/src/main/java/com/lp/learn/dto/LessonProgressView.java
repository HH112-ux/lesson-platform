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

    private final boolean completed;

    private final LocalDateTime updatedAt;

    /**
     * Creates one lesson progress.
     *
     * @param lessonId lesson id
     * @param positionSeconds latest position in seconds
     * @param maxPositionSeconds historical maximum in seconds
     * @param completed whether the server marked it complete
     * @param updatedAt last update time
     * @author Caspian
     * @since 0.1.0
     */
    public LessonProgressView(
            long lessonId,
            int positionSeconds,
            int maxPositionSeconds,
            boolean completed,
            LocalDateTime updatedAt) {
        this.lessonId = lessonId;
        this.positionSeconds = positionSeconds;
        this.maxPositionSeconds = maxPositionSeconds;
        this.completed = completed;
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
     * @return maximum position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getMaxPositionSeconds() {
        return maxPositionSeconds;
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
