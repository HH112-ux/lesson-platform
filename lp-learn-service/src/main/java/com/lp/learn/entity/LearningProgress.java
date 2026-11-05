package com.lp.learn.entity;

import java.time.LocalDateTime;

/**
 * Row read from and written to {@code learning_progress}.
 *
 * <p>{@code maxPositionSeconds} only grows; {@code positionSeconds} stores the latest reported
 * point. {@code completed} is derived by the service, never accepted from a client.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LearningProgress {

    private Long id;

    private Long userId;

    private Long courseId;

    private Long lessonId;

    private Integer positionSeconds;

    private Integer maxPositionSeconds;

    private Integer completed;

    private LocalDateTime updatedAt;

    /**
     * Returns the primary key.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the primary key.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the student id.
     *
     * @return student user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the student id.
     *
     * @param userId student user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the lesson id.
     *
     * @return lesson id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getLessonId() {
        return lessonId;
    }

    /**
     * Sets the lesson id.
     *
     * @param lessonId lesson id
     * @author Caspian
     * @since 0.1.0
     */
    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    /**
     * Returns the latest reported position.
     *
     * @return position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getPositionSeconds() {
        return positionSeconds;
    }

    /**
     * Sets the latest reported position.
     *
     * @param positionSeconds position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setPositionSeconds(Integer positionSeconds) {
        this.positionSeconds = positionSeconds;
    }

    /**
     * Returns the historical maximum position.
     *
     * @return maximum position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getMaxPositionSeconds() {
        return maxPositionSeconds;
    }

    /**
     * Sets the historical maximum position.
     *
     * @param maxPositionSeconds maximum position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setMaxPositionSeconds(Integer maxPositionSeconds) {
        this.maxPositionSeconds = maxPositionSeconds;
    }

    /**
     * Returns the completion flag.
     *
     * @return 1 when complete, 0 otherwise
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getCompleted() {
        return completed;
    }

    /**
     * Sets the completion flag.
     *
     * @param completed 1 when complete, 0 otherwise
     * @author Caspian
     * @since 0.1.0
     */
    public void setCompleted(Integer completed) {
        this.completed = completed;
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

    /**
     * Sets the last update time.
     *
     * @param updatedAt update time
     * @author Caspian
     * @since 0.1.0
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
