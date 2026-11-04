package com.lp.course.entity;

/**
 * Lesson row read from the {@code course_lesson} table.
 *
 * <p>Trade only needs the outline plus the trial flag, so video metadata is not projected.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseLesson {

    private Long id;

    private Long courseId;

    private Long chapterId;

    private String title;

    private Integer isFree;

    private Integer sort;

    /**
     * Returns the lesson id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the lesson id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the owning course id.
     *
     * @return {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the owning course id.
     *
     * @param courseId {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the chapter id.
     *
     * @return {@code course_chapter.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getChapterId() {
        return chapterId;
    }

    /**
     * Sets the chapter id.
     *
     * @param chapterId {@code course_chapter.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
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
     * Sets the lesson title.
     *
     * @param title title text
     * @author Caspian
     * @since 0.1.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the trial flag as stored in the database.
     *
     * @return 1 trial, 0 requires purchase
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getIsFree() {
        return isFree;
    }

    /**
     * Sets the trial flag.
     *
     * @param isFree 1 trial, 0 requires purchase
     * @author Caspian
     * @since 0.1.0
     */
    public void setIsFree(Integer isFree) {
        this.isFree = isFree;
    }

    /**
     * Returns the display order inside the chapter.
     *
     * @return sort value
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Sets the display order inside the chapter.
     *
     * @param sort sort value
     * @author Caspian
     * @since 0.1.0
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
