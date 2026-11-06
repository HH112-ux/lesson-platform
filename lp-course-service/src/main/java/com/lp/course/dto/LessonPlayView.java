package com.lp.course.dto;

/**
 * Lesson projection used by learn-service to sign playback URLs.
 *
 * <p>The media object key is exposed only to learn-service; it must never be returned to a client
 * before it has been signed.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LessonPlayView {

    private final long lessonId;

    private final long courseId;

    private final String videoObjectKey;

    private final int durationSec;

    private final boolean free;

    private final int courseStatus;

    /**
     * Creates a fully populated projection.
     *
     * @param lessonId lesson primary key
     * @param courseId owning course id
     * @param videoObjectKey MinIO object key
     * @param durationSec video duration in seconds
     * @param free whether the lesson is a free trial
     * @param courseStatus status of the owning course
     * @author Caspian
     * @since 0.1.0
     */
    public LessonPlayView(
            long lessonId,
            long courseId,
            String videoObjectKey,
            int durationSec,
            boolean free,
            int courseStatus) {
        this.lessonId = lessonId;
        this.courseId = courseId;
        this.videoObjectKey = videoObjectKey;
        this.durationSec = durationSec;
        this.free = free;
        this.courseStatus = courseStatus;
    }

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
     * Returns the owning course id.
     *
     * @return course id used for the entitlement check
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
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
     * Returns the video duration.
     *
     * @return duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getDurationSec() {
        return durationSec;
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
     * Returns the status of the owning course.
     *
     * @return 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public int getCourseStatus() {
        return courseStatus;
    }
}
