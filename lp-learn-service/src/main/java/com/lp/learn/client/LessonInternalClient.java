package com.lp.learn.client;

import com.lp.learn.dto.LessonPlayInfo;

/**
 * Reads lesson media metadata from course-service.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface LessonInternalClient {

    /**
     * Loads one playable lesson.
     *
     * @param lessonId lesson primary key
     * @return lesson media metadata
     * @throws com.lp.common.exception.BizException with {@code 40401} when the lesson is absent
     * @author Caspian
     * @since 0.1.0
     */
    LessonPlayInfo lesson(long lessonId);
}
