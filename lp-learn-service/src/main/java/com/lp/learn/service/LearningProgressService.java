package com.lp.learn.service;

import com.lp.learn.dto.CourseProgressView;
import com.lp.learn.dto.LessonProgressView;

/**
 * Records playback progress and recomputes completion on the server.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface LearningProgressService {

    /**
     * Records one position report.
     *
     * @param userId student user id
     * @param lessonId lesson id
     * @param positionSeconds reported position
     * @return stored progress with the server computed completion flag
     * @throws com.lp.common.exception.BizException {@code 40401} missing lesson, {@code 40302} no
     *     entitlement, {@code 63001} position out of range
     * @author Caspian
     * @since 0.1.0
     */
    LessonProgressView report(long userId, long lessonId, int positionSeconds);

    /**
     * Returns the student's progress for one course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return reported lessons, possibly empty
     * @throws com.lp.common.exception.BizException {@code 40302} when the course is not owned
     * @author Caspian
     * @since 0.1.0
     */
    CourseProgressView courseProgress(long userId, long courseId);
}
