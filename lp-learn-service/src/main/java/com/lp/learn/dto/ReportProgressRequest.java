package com.lp.learn.dto;

/**
 * Body of a progress report.
 *
 * <p>There is deliberately no {@code completed}, {@code courseId} or {@code userId}: the server
 * derives the completion flag and takes the course from the lesson, and the student from the
 * gateway header.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class ReportProgressRequest {

    private Long lessonId;

    private Integer positionSeconds;

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
     * Returns the reported playback position.
     *
     * @return position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getPositionSeconds() {
        return positionSeconds;
    }

    /**
     * Sets the reported playback position.
     *
     * @param positionSeconds position in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setPositionSeconds(Integer positionSeconds) {
        this.positionSeconds = positionSeconds;
    }
}
