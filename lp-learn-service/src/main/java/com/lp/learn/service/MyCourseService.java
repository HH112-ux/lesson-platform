package com.lp.learn.service;

import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;

/**
 * Builds the "my courses" list from entitlements joined with course display data.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface MyCourseService {

    /**
     * Returns one page of courses the student may access.
     *
     * @param userId student user id
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return paged courses; an empty page when the student owns nothing
     * @throws com.lp.common.exception.BizException when course-service fails
     * @author Caspian
     * @since 0.1.0
     */
    PageView<MyCourseView> myCourses(long userId, int pageNum, int pageSize);
}
