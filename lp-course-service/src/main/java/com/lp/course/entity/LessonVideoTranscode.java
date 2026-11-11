package com.lp.course.entity;

import java.time.LocalDateTime;

/**
 * One asynchronous transcode task stored in {@code lesson_video_transcode}.
 *
 * <p>A row is keyed by the source object key, so a repeated confirmation of the same upload finds
 * the existing row instead of queueing the object twice. The source object stays untouched; a
 * successful run only fills {@link #outputObjectKey} and moves {@link #status} to READY.
 *
 * @author Caspian
 * @since 0.11.0
 */
public class LessonVideoTranscode {

    private Long id;

    private Long lessonId;

    private Long uploadId;

    private String sourceObjectKey;

    private Long sourceSizeBytes;

    private String outputObjectKey;

    private Integer status;

    private Integer attemptCount;

    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Returns the task id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.11.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the task id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.11.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the lesson the source video belongs to.
     *
     * @return {@code course_lesson.id}
     * @author Caspian
     * @since 0.11.0
     */
    public Long getLessonId() {
        return lessonId;
    }

    /**
     * Sets the lesson the source video belongs to.
     *
     * @param lessonId {@code course_lesson.id}
     * @author Caspian
     * @since 0.11.0
     */
    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    /**
     * Returns the upload session that produced the source object.
     *
     * @return {@code lesson_upload.id}
     * @author Caspian
     * @since 0.11.0
     */
    public Long getUploadId() {
        return uploadId;
    }

    /**
     * Sets the upload session that produced the source object.
     *
     * @param uploadId {@code lesson_upload.id}
     * @author Caspian
     * @since 0.11.0
     */
    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * Returns the stored source object key.
     *
     * @return object key of the original upload, never a URL
     * @author Caspian
     * @since 0.11.0
     */
    public String getSourceObjectKey() {
        return sourceObjectKey;
    }

    /**
     * Sets the stored source object key.
     *
     * @param sourceObjectKey object key of the original upload
     * @author Caspian
     * @since 0.11.0
     */
    public void setSourceObjectKey(String sourceObjectKey) {
        this.sourceObjectKey = sourceObjectKey;
    }

    /**
     * Returns the size of the source object.
     *
     * @return size in bytes recorded at confirmation
     * @author Caspian
     * @since 0.11.0
     */
    public Long getSourceSizeBytes() {
        return sourceSizeBytes;
    }

    /**
     * Sets the size of the source object.
     *
     * @param sourceSizeBytes size in bytes recorded at confirmation
     * @author Caspian
     * @since 0.11.0
     */
    public void setSourceSizeBytes(Long sourceSizeBytes) {
        this.sourceSizeBytes = sourceSizeBytes;
    }

    /**
     * Returns the transcoded object key.
     *
     * @return output key, {@code null} until the task is READY
     * @author Caspian
     * @since 0.11.0
     */
    public String getOutputObjectKey() {
        return outputObjectKey;
    }

    /**
     * Sets the transcoded object key.
     *
     * @param outputObjectKey output key under the {@code transcoded/} prefix
     * @author Caspian
     * @since 0.11.0
     */
    public void setOutputObjectKey(String outputObjectKey) {
        this.outputObjectKey = outputObjectKey;
    }

    /**
     * Returns the task status.
     *
     * @return 0 pending, 1 running, 2 ready, 3 failed, see {@code LessonTranscodeStatus}
     * @author Caspian
     * @since 0.11.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the task status.
     *
     * @param status 0 pending, 1 running, 2 ready, 3 failed
     * @author Caspian
     * @since 0.11.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns how many times the task has been executed.
     *
     * @return attempt count, never above the configured limit
     * @author Caspian
     * @since 0.11.0
     */
    public Integer getAttemptCount() {
        return attemptCount;
    }

    /**
     * Sets how many times the task has been executed.
     *
     * @param attemptCount attempt count
     * @author Caspian
     * @since 0.11.0
     */
    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    /**
     * Returns the last failure reason.
     *
     * @return reason text, {@code null} while no attempt failed
     * @author Caspian
     * @since 0.11.0
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Sets the last failure reason.
     *
     * @param failureReason reason text
     * @author Caspian
     * @since 0.11.0
     */
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    /**
     * Returns the time the first attempt started.
     *
     * @return start time, {@code null} while the task never ran
     * @author Caspian
     * @since 0.11.0
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the time the first attempt started.
     *
     * @param startedAt start time
     * @author Caspian
     * @since 0.11.0
     */
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Returns the time the task reached a final state.
     *
     * @return finish time, {@code null} until READY or the final failure
     * @author Caspian
     * @since 0.11.0
     */
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    /**
     * Sets the time the task reached a final state.
     *
     * @param finishedAt finish time
     * @author Caspian
     * @since 0.11.0
     */
    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    /**
     * Returns the creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.11.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.11.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the last update time.
     *
     * @return update time
     * @author Caspian
     * @since 0.11.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update time.
     *
     * @param updatedAt update time
     * @author Caspian
     * @since 0.11.0
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
