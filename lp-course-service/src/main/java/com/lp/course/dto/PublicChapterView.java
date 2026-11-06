package com.lp.course.dto;

import java.util.List;

/**
 * One chapter of the public syllabus with its lessons in display order.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PublicChapterView {

    private final long chapterId;

    private final String title;

    private final int sort;

    private final List<PublicLessonView> lessons;

    /**
     * Creates a chapter row.
     *
     * @param chapterId chapter primary key
     * @param title chapter title
     * @param sort display order
     * @param lessons lessons inside the chapter
     * @author Caspian
     * @since 0.1.0
     */
    public PublicChapterView(
            long chapterId, String title, int sort, List<PublicLessonView> lessons) {
        this.chapterId = chapterId;
        this.title = title;
        this.sort = sort;
        this.lessons = lessons;
    }

    /**
     * Returns the chapter id.
     *
     * @return chapter primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getChapterId() {
        return chapterId;
    }

    /**
     * Returns the chapter title.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
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
     * Returns the lessons of this chapter.
     *
     * @return lessons in display order
     * @author Caspian
     * @since 0.1.0
     */
    public List<PublicLessonView> getLessons() {
        return lessons;
    }
}
