package com.lp.learn.client;

import java.util.List;

/**
 * Reads course display data from course-service.
 *
 * <p>Abstracted so my-courses can be unit tested without a running course-service. The HTTP
 * implementation lives in {@link HttpCourseInternalClient}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CourseInternalClient {

    /**
     * Loads a batch of courses.
     *
     * @param courseIds course ids, may be empty
     * @return snapshots of the ids that exist, possibly empty
     * @throws com.lp.common.exception.BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseSnapshot> courses(List<Long> courseIds);
}
