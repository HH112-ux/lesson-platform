package com.lp.course.service;

import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.LessonInternalView;
import java.util.List;

/**
 * Read-only course queries exposed to other domain services.
 *
 * <p>This facade is not part of the public API: routes live under {@code /internal/v1/**} and the
 * gateway never forwards them.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CourseInternalService {

    /**
     * Loads one course for trade validation.
     *
     * @param courseId course primary key
     * @return course projection with price in fen and publishing status
     * @author Caspian
     * @since 0.1.0
     */
    CourseInternalView course(long courseId);

    /**
     * Loads a batch of courses, preserving no particular order.
     *
     * @param courseIds course primary keys, may be empty
     * @return projections of the ids that exist, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseInternalView> courses(List<Long> courseIds);

    /**
     * Lists the visible lessons of a course.
     *
     * @param courseId course primary key
     * @return lesson outline ordered by chapter and sort
     * @author Caspian
     * @since 0.1.0
     */
    List<LessonInternalView> lessons(long courseId);
}
