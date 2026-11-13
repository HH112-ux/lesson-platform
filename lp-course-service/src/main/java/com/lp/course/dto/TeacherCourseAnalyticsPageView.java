package com.lp.course.dto;

import java.util.List;

/**
 * One page of the teacher course analytics table.
 *
 * @param pageNum one based page number
 * @param pageSize effective page size
 * @param total total owned course count
 * @param records rows of this page
 * @author Caspian
 * @since 0.16.0
 */
public record TeacherCourseAnalyticsPageView(
        int pageNum, int pageSize, long total, List<TeacherCourseAnalyticsView> records) {}
