package com.lp.course.dto;

/**
 * One lesson row of the public syllabus.
 *
 * <p>The video object key is never exposed: playback URLs are issued by the learn service after an
 * entitlement check. {@code playable} only tells the page whether the lesson is a trial lesson or
 * the caller already owns the course; the authoritative gate stays on the playback endpoint.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PublicLessonView {

    private final long lessonId;

    private final String title;

    private final boolean free;

    private final int durationSec;

    private final int sort;

    private final boolean playable;

    /**
     * Creates a syllabus lesson.
     *
     * @param lessonId lesson primary key
     * @param title lesson title
     * @param free whether the lesson is a trial lesson
     * @param durationSec duration in seconds, {@code 0} when unknown
     * @param sort display order inside the chapter
     * @param playable whether the caller may start playback directly
     * @author Caspian
     * @since 0.1.0
     */
    public PublicLessonView(
            long lessonId,
            String title,
            boolean free,
            int durationSec,
            int sort,
            boolean playable) {
        this.lessonId = lessonId;
        this.title = title;
        this.free = free;
        this.durationSec = durationSec;
        this.sort = sort;
        this.playable = playable;
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
     * Returns the lesson title.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns whether this is a trial lesson.
     *
     * @return {@code true} for a trial lesson
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isFree() {
        return free;
    }

    /**
     * Returns the duration.
     *
     * @return duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getDurationSec() {
        return durationSec;
    }

    /**
     * Returns the display order.
     *
     * @return sort value
     * @author Caspian
     * @since 0.1.0
     */
    public int getSort() {
        return sort;
    }

    /**
     * Returns whether the caller may start playback directly.
     *
     * @return {@code true} when the lesson is a trial or the course is owned
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isPlayable() {
        return playable;
    }
}
