package com.lp.trade.client;

import com.lp.trade.dto.CourseSnapshot;
import java.util.List;

/**
 * Reads course data from course-service.
 *
 * <p>Abstracted so cart and order logic can be unit tested without a running course-service. The
 * HTTP implementation lives in {@link HttpCourseInternalClient}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CourseInternalClient {

    /**
     * Loads one course.
     *
     * @param courseId course primary key
     * @return course snapshot including price in fen and publishing status
     * @throws com.lp.common.exception.BizException when the course is missing or the upstream call
     *     fails
     * @author Caspian
     * @since 0.1.0
     */
    CourseSnapshot course(long courseId);

    /**
     * Loads a batch of courses.
     *
     * @param courseIds course primary keys, may be empty
     * @return snapshots of the ids that exist, possibly empty
     * @throws com.lp.common.exception.BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseSnapshot> courses(List<Long> courseIds);
}
