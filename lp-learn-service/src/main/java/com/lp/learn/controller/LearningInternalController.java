package com.lp.learn.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.learn.dto.CourseProgressSummaryView;
import com.lp.learn.dto.TeacherLearningMetricsView;
import com.lp.learn.service.LearningAnalyticsService;
import com.lp.learn.service.MyCourseService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal learning API used by trusted services only.
 *
 * <p>Trade-service calls the progress summary to decide refund admission. The shared internal token
 * filter guards the path; a missing token never reaches this controller.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/learning")
public class LearningInternalController {

    private static final int MAX_ANALYTICS_COURSES = 200;

    private final MyCourseService myCourseService;

    private final LearningAnalyticsService learningAnalyticsService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param myCourseService progress summary builder
     * @param learningAnalyticsService teacher dashboard aggregation
     * @author Caspian
     * @since 0.1.0
     */
    public LearningInternalController(
            MyCourseService myCourseService, LearningAnalyticsService learningAnalyticsService) {
        this.myCourseService = myCourseService;
        this.learningAnalyticsService = learningAnalyticsService;
    }

    /**
     * Returns the completed share of one student in one course.
     *
     * @param userId student user id
     * @param courseId course id
     * @param request current request, used to resolve the trace id
     * @return completed lessons, lesson count and progress percent
     * @throws BizException with {@code 40001} when a parameter is missing
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/progress-summary")
    public Result<CourseProgressSummaryView> progressSummary(
            @RequestParam("userId") long userId,
            @RequestParam("courseId") long courseId,
            HttpServletRequest request) {
        if (userId <= 0 || courseId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId and courseId are required");
        }
        return Result.ok(
                myCourseService.progressSummary(userId, courseId), TraceIds.resolve(request));
    }

    /**
     * Returns learning aggregates of the given courses for the teacher dashboard.
     *
     * <p>The course-service resolves the caller's teacher profile and verifies ownership before
     * calling this endpoint. Only course ids are accepted here: the learn-service holds no teacher
     * attribution and must never be asked to filter by teacher.
     *
     * @param courseIds comma separated owned course ids, at most {@code 200}
     * @param days natural day window, clamped by the service
     * @param request current request, used to resolve the trace id
     * @return raw counters and the daily active series
     * @throws BizException with {@code 40001} when the course list is missing, invalid or too long
     * @author Caspian
     * @since 0.16.0
     */
    @GetMapping("/teacher-analytics")
    public Result<TeacherLearningMetricsView> teacherAnalytics(
            @RequestParam("courseIds") String courseIds,
            @RequestParam(value = "days", defaultValue = "30") int days,
            HttpServletRequest request) {
        List<Long> ids = parseCourseIds(courseIds);
        return Result.ok(
                learningAnalyticsService.teacherMetrics(ids, days), TraceIds.resolve(request));
    }

    private List<Long> parseCourseIds(String courseIds) {
        if (courseIds == null || courseIds.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseIds is required");
        }
        List<Long> ids = new ArrayList<>();
        for (String token : courseIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(trimmed);
            } catch (NumberFormatException exception) {
                throw new BizException(ErrorCode.PARAM_INVALID, "courseIds must be numeric");
            }
            if (id <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "courseIds must be positive");
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseIds is required");
        }
        if (ids.size() > MAX_ANALYTICS_COURSES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "too many courseIds");
        }
        return ids;
    }
}
