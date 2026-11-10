package com.lp.course.domain;

/**
 * Status codes of {@code lesson_upload.status}.
 *
 * <p>A session starts as {@link #PENDING} when the service issues a presigned upload credential and
 * only becomes {@link #CONFIRMED} after the server has confirmed the object exists with {@code
 * statObject}. The status drives the generated {@code pending_flag} column, so only one pending
 * session can exist per lesson: moving a row out of {@link #PENDING} releases the unique key {@code
 * uk_lesson_pending}.
 *
 * @author Caspian
 * @since 0.7.0
 */
public final class LessonUploadStatus {

    /** The credential was issued and the object has not been confirmed yet: {@code 0}. */
    public static final int PENDING = 0;

    /** The object was confirmed and the lesson video metadata can be written: {@code 1}. */
    public static final int CONFIRMED = 1;

    /** The session outlived {@code session_expires_at} before confirmation: {@code 2}. */
    public static final int EXPIRED = 2;

    /** The teacher abandoned the session, for example by restarting the upload: {@code 3}. */
    public static final int ABORTED = 3;

    /**
     * Prevents instantiation of this constant holder.
     *
     * @author Caspian
     * @since 0.7.0
     */
    private LessonUploadStatus() {}
}
