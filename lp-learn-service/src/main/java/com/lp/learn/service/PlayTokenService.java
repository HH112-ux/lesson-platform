package com.lp.learn.service;

import com.lp.learn.dto.PlayTokenView;

/**
 * Issues short lived playback URLs after checking course access.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PlayTokenService {

    /**
     * Signs the lesson video for one student.
     *
     * @param userId student user id
     * @param lessonId lesson primary key
     * @return presigned URL and its expiry
     * @throws com.lp.common.exception.BizException with {@code 40401} when the lesson is missing,
     *     {@code 40302} when the course is not owned, {@code 50206} when storage is not configured
     * @author Caspian
     * @since 0.1.0
     */
    PlayTokenView issue(long userId, long lessonId);
}
