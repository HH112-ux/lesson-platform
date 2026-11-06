package com.lp.learn.dto;

/**
 * Lesson projection returned by the course-service internal API.
 *
 * <p>Also used as the Jackson binding type, so it keeps setters. The object key must stay
 * server-side and is never serialised to a client.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LessonPlayInfo {

    private long lessonId;

    private long courseId;

    private String videoObjectKey;

    private int durationSec;

    private boolean free;

    private int courseStatus;

    /**
     * Returns the lesson id.
     *
     * @return lesson primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getLessonId() {
        return lessonId;
    }

    /**
     * Sets the lesson id.
     *
     * @param lessonId lesson primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setLessonId(long lessonId) {
        this.lessonId = lessonId;
    }

    /**
     * Returns the owning course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Sets the owning course id.
     *
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the media object key.
     *
     * @return MinIO object key
     * @author Caspian
     * @since 0.1.0
     */
    public String getVideoObjectKey() {
        return videoObjectKey;
    }

    /**
     * Sets the media object key.
     *
     * @param videoObjectKey MinIO object key
     * @author Caspian
     * @since 0.1.0
     */
    public void setVideoObjectKey(String videoObjectKey) {
        this.videoObjectKey = videoObjectKey;
    }

    /**
     * Returns the video duration.
     *
     * @return duration in seconds, {@code 0} when unknown
     * @author Caspian
     * @since 0.1.0
     */
    public int getDurationSec() {
        return durationSec;
    }

    /**
     * Sets the video duration.
     *
     * @param durationSec duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    /**
     * Tells whether the lesson is a free trial.
     *
     * @return {@code true} when no entitlement is required
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isFree() {
        return free;
    }

    /**
     * Sets the free trial flag.
     *
     * @param free {@code true} when no entitlement is required
     * @author Caspian
     * @since 0.1.0
     */
    public void setFree(boolean free) {
        this.free = free;
    }

    /**
     * Returns the status of the owning course.
     *
     * @return 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public int getCourseStatus() {
        return courseStatus;
    }

    /**
     * Sets the status of the owning course.
     *
     * @param courseStatus 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseStatus(int courseStatus) {
        this.courseStatus = courseStatus;
    }
}
