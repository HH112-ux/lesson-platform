package com.lp.learn.dto;

import java.util.List;

/**
 * Progress of all reported lessons in one course.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseProgressView {

    private final long courseId;

    private final List<LessonProgressView> lessons;

    /**
     * Creates one course progress payload.
     *
     * @param courseId course id
     * @param lessons reported lessons
     * @author Caspian
     * @since 0.1.0
     */
    public CourseProgressView(long courseId, List<LessonProgressView> lessons) {
        this.courseId = courseId;
        this.lessons = lessons;
    }

    /**
     * Returns the course id.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Returns the reported lessons.
     *
     * @return immutable lesson list
     * @author Caspian
     * @since 0.1.0
     */
    public List<LessonProgressView> getLessons() {
        return lessons;
    }
}
