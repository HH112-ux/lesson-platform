package com.lp.course.service;

import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseView;

/**
 * Teacher console use cases for the courses a teacher owns.
 *
 * <p>Ownership is enforced in the service layer against {@code course.owner_teacher_id}; the caller
 * supplied user id is never trusted beyond the gateway-injected header.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface TeacherCourseService {

    /**
     * Lists one page of the caller's courses.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param status optional course status filter
     * @param page one based page number
     * @param size page size, capped at 100
     * @return one page of owned courses
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCoursePageView list(long userId, String roles, Integer status, int page, int size);

    /**
     * Creates a draft course owned by the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param request course form
     * @return the created course
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView create(long userId, String roles, TeacherCourseRequest request);

    /**
     * Edits a draft or offline course; editing an offline course sends it back to draft.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request course form, omitted fields keep their current value
     * @return the course after the update
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView update(
            long userId, String roles, long courseId, TeacherCourseRequest request);

    /**
     * Loads one course visible to the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return course detail
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView detail(long userId, String roles, long courseId);

    /**
     * Moves a draft or offline course into the review queue.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return the course in pending-review status
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView submit(long userId, String roles, long courseId);
}
