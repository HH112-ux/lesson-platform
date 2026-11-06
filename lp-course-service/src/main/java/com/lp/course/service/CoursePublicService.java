package com.lp.course.service;

import com.lp.course.dto.PublicCourseView;

/**
 * Read-only course queries for the public web API.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CoursePublicService {

    /**
     * Loads the sellable projection of one course.
     *
     * @param courseId course primary key
     * @return public course projection, including the status for offline courses
     * @author Caspian
     * @since 0.1.0
     */
    PublicCourseView course(long courseId);
}
