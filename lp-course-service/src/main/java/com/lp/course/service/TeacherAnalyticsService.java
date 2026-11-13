package com.lp.course.service;

import com.lp.course.dto.TeacherAnalyticsOverviewView;
import com.lp.course.dto.TeacherCourseAnalyticsDetailView;
import com.lp.course.dto.TeacherCourseAnalyticsPageView;

/**
 * Teacher learning dashboard, always scoped to the caller's own courses.
 *
 * <p>Every method resolves the caller's {@code teacher_profile} and never accepts a teacher id from
 * the client. An administrator sees exactly the same scope as a teacher: platform wide numbers live
 * in the operations dashboard.
 *
 * @author Caspian
 * @since 0.16.0
 */
public interface TeacherAnalyticsService {

    /**
     * Returns the teacher wide summary.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param days requested natural day window
     * @return summary including the daily active series
     * @author Caspian
     * @since 0.16.0
     */
    TeacherAnalyticsOverviewView overview(long userId, String roles, int days);

    /**
     * Returns one page of the teacher's course analytics.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param days requested natural day window
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return page of course rows
     * @author Caspian
     * @since 0.16.0
     */
    TeacherCourseAnalyticsPageView courses(
            long userId, String roles, int days, int pageNum, int pageSize);

    /**
     * Returns the progress distribution and lesson completion of one owned course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course id
     * @param days requested natural day window
     * @return course detail
     * @author Caspian
     * @since 0.16.0
     */
    TeacherCourseAnalyticsDetailView course(long userId, String roles, long courseId, int days);
}
