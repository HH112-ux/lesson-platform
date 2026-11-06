package com.lp.course.dto;

import java.util.List;

/**
 * Public syllabus of one published course.
 *
 * <p>The outline is visible to anonymous callers, while the {@code playable} flag of each lesson
 * tells whether that caller may start playback without buying.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseSyllabusView {

    private final long courseId;

    private final boolean owned;

    private final List<PublicChapterView> chapters;

    /**
     * Creates a syllabus projection.
     *
     * @param courseId course primary key
     * @param owned whether the caller already owns the course
     * @param chapters chapters in display order
     * @author Caspian
     * @since 0.1.0
     */
    public CourseSyllabusView(long courseId, boolean owned, List<PublicChapterView> chapters) {
        this.courseId = courseId;
        this.owned = owned;
        this.chapters = chapters;
    }

    /**
     * Returns the course id.
     *
     * @return course primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Returns whether the caller already owns the course.
     *
     * @return {@code true} when an active entitlement exists; always {@code false} when anonymous
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isOwned() {
        return owned;
    }

    /**
     * Returns the chapters.
     *
     * @return chapters in display order
     * @author Caspian
     * @since 0.1.0
     */
    public List<PublicChapterView> getChapters() {
        return chapters;
    }
}
