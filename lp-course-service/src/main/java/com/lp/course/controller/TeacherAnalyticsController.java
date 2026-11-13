package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.TeacherAnalyticsOverviewView;
import com.lp.course.dto.TeacherCourseAnalyticsDetailView;
import com.lp.course.dto.TeacherCourseAnalyticsPageView;
import com.lp.course.service.TeacherAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher learning dashboard endpoints.
 *
 * <p>Every endpoint is read-only. The caller's teacher profile is resolved in the service and the
 * learn-service is only asked for course ids that already passed the ownership check, so there is
 * no request shape that can read another teacher's data.
 *
 * @author Caspian
 * @since 0.16.0
 */
@RestController
@RequestMapping("/api/v1/teacher/analytics")
public class TeacherAnalyticsController {

    private final TeacherAnalyticsService teacherAnalyticsService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param teacherAnalyticsService teacher dashboard use cases
     * @author Caspian
     * @since 0.16.0
     */
    public TeacherAnalyticsController(TeacherAnalyticsService teacherAnalyticsService) {
        this.teacherAnalyticsService = teacherAnalyticsService;
    }

    /**
     * Returns the teacher wide summary.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param days natural day window, defaults to 30
     * @param request current request, used to resolve the trace id
     * @return summary including the daily active series
     * @author Caspian
     * @since 0.16.0
     */
    @GetMapping("/overview")
    public Result<TeacherAnalyticsOverviewView> overview(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestParam(value = "days", defaultValue = "30") int days,
            HttpServletRequest request) {
        return Result.ok(
                teacherAnalyticsService.overview(requireUser(userId), roles, days),
                TraceIds.resolve(request));
    }

    /**
     * Returns one page of the teacher's course analytics.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param days natural day window, defaults to 30
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return page of course rows
     * @author Caspian
     * @since 0.16.0
     */
    @GetMapping("/courses")
    public Result<TeacherCourseAnalyticsPageView> courses(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestParam(value = "days", defaultValue = "30") int days,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                teacherAnalyticsService.courses(
                        requireUser(userId), roles, days, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Returns the progress distribution and lesson completion of one owned course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param days natural day window, defaults to 30
     * @param request current request, used to resolve the trace id
     * @return course detail
     * @author Caspian
     * @since 0.16.0
     */
    @GetMapping("/courses/{courseId}")
    public Result<TeacherCourseAnalyticsDetailView> course(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @RequestParam(value = "days", defaultValue = "30") int days,
            HttpServletRequest request) {
        return Result.ok(
                teacherAnalyticsService.course(requireUser(userId), roles, courseId, days),
                TraceIds.resolve(request));
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
