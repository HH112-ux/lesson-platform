package com.lp.course.domain;

/**
 * Status codes of {@code lesson_video_transcode.status}.
 *
 * <p>A task starts as {@link #PENDING} when an upload is confirmed, becomes {@link #RUNNING} while
 * {@code ffmpeg} works on the source object, and ends as either {@link #READY} with an output key
 * or {@link #FAILED} after the retry limit. Only {@link #READY} makes playback prefer the
 * transcoded object; every other state keeps the original upload playable.
 *
 * @author Caspian
 * @since 0.11.0
 */
public final class LessonTranscodeStatus {

    /** The task was recorded and is waiting for a worker: {@code 0}. */
    public static final int PENDING = 0;

    /** A worker is transcoding the source object: {@code 1}. */
    public static final int RUNNING = 1;

    /** The transcoded object is stored and may be used for playback: {@code 2}. */
    public static final int READY = 2;

    /** Every attempt failed; {@code failure_reason} holds the last reason: {@code 3}. */
    public static final int FAILED = 3;

    /**
     * Prevents instantiation of this constant holder.
     *
     * @author Caspian
     * @since 0.11.0
     */
    private LessonTranscodeStatus() {}
}
