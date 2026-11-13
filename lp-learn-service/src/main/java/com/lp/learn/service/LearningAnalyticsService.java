package com.lp.learn.service;

import com.lp.learn.dto.TeacherLearningMetricsView;
import java.util.List;

/**
 * Read-only learning aggregations for the teacher dashboard.
 *
 * @author Caspian
 * @since 0.16.0
 */
public interface LearningAnalyticsService {

    /**
     * Aggregates learning behaviour of the given courses.
     *
     * <p>The caller must have verified that every course id belongs to the teacher. This service
     * never receives or resolves a teacher id on purpose.
     *
     * @param courseIds owned course ids, at most {@code 200}; empty yields a zero result
     * @param days natural day window, clamped to {@code 1..365}
     * @return raw counters, percentages are left to the caller
     * @author Caspian
     * @since 0.16.0
     */
    TeacherLearningMetricsView teacherMetrics(List<Long> courseIds, int days);
}
