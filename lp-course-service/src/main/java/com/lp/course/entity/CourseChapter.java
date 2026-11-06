package com.lp.course.entity;

/**
 * Chapter row read from the {@code course_chapter} table.
 *
 * <p>Only the columns needed by the public syllabus are projected.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseChapter {

    private Long id;

    private Long courseId;

    private String title;

    private Integer sort;

    /**
     * Returns the chapter id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the chapter id.
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
     * Sets the chapter title.
     *
     * @param title title text
     * @author Caspian
     * @since 0.1.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the display order.
     *
     * @return sort value
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Sets the display order.
     *
     * @param sort sort value
     * @author Caspian
     * @since 0.1.0
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
