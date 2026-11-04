package com.lp.course.dto;

/**
 * Internal lesson projection consumed by the trade and learning services.
 *
 * <p>Only outline data and the trial flag are exposed; media identifiers stay private to
 * course-service.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LessonInternalView {

    private final long lessonId;

    private final long chapterId;

    private final String title;

    private final boolean free;

    private final int sort;

    /**
     * Creates a fully populated projection.
     *
     * @param lessonId lesson primary key
     * @param chapterId owning chapter id
     * @param title lesson title
     * @param free whether the lesson is a free trial
     * @param sort display order inside the chapter
     * @author Caspian
     * @since 0.1.0
     */
    public LessonInternalView(long lessonId, long chapterId, String title, boolean free, int sort) {
        this.lessonId = lessonId;
        this.chapterId = chapterId;
        this.title = title;
        this.free = free;
        this.sort = sort;
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
     * Returns the owning chapter id.
     *
     * @return chapter primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getChapterId() {
        return chapterId;
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
     * Tells whether the lesson is a free trial.
     *
     * @return {@code true} for a free trial lesson
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isFree() {
        return free;
    }

    /**
     * Returns the display order inside the chapter.
     *
     * @return sort value
     * @author Caspian
     * @since 0.1.0
     */
    public int getSort() {
        return sort;
    }
}
