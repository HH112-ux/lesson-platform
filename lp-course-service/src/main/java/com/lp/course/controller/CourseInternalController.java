package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CourseReviewRequest;
import com.lp.course.dto.CourseReviewResultView;
import com.lp.course.dto.CourseStatsView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.dto.PendingCoursePageView;
import com.lp.course.service.CourseInternalService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service course queries.
 *
 * <p>Routes use the {@code /internal/v1} prefix, which the gateway does not route or whitelist, so
 * they are unreachable from the public edge.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/courses")
public class CourseInternalController {

    private final CourseInternalService courseInternalService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param courseInternalService internal course queries
     * @author Caspian
     * @since 0.1.0
     */
    public CourseInternalController(CourseInternalService courseInternalService) {
        this.courseInternalService = courseInternalService;
    }

    /**
     * Returns the detail projection of one course.
     *
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return course detail, {@code 40401} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}")
    public Result<CourseInternalView> detail(
            @PathVariable long courseId, HttpServletRequest request) {
        return Result.ok(courseInternalService.course(courseId), TraceIds.resolve(request));
    }

    /**
     * Returns a batch of course projections.
     *
     * @param ids comma separated course ids, for example {@code ?ids=1,2}
     * @param request current request, used to resolve the trace id
     * @return matching projections, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<List<CourseInternalView>> list(
            @RequestParam(value = "ids", required = false) List<Long> ids,
            HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ids is required");
        }
        return Result.ok(courseInternalService.courses(ids), TraceIds.resolve(request));
    }

    /**
     * Returns the lesson outline of one course.
     *
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return lessons, {@code 40401} when the course is absent
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}/lessons")
    public Result<List<LessonInternalView>> lessons(
            @PathVariable long courseId, HttpServletRequest request) {
        return Result.ok(courseInternalService.lessons(courseId), TraceIds.resolve(request));
    }

    /**
     * Returns one page of courses waiting for the operations review.
     *
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return pending courses, oldest submission first
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/pending")
    public Result<PendingCoursePageView> pending(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                courseInternalService.pendingCourses(pageNum, pageSize), TraceIds.resolve(request));
    }

    /**
     * Returns course counts grouped by status.
     *
     * @param request current request, used to resolve the trace id
     * @return course statistics for the operations dashboard
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/stats")
    public Result<CourseStatsView> stats(HttpServletRequest request) {
        return Result.ok(courseInternalService.stats(), TraceIds.resolve(request));
    }

    /**
     * Applies a review decision to a pending course.
     *
     * @param courseId course primary key
     * @param body decision, optional price in fen and the operator id
     * @param request current request, used to resolve the trace id
     * @return course state after the decision
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{courseId}/review")
    public Result<CourseReviewResultView> review(
            @PathVariable long courseId,
            @RequestBody CourseReviewRequest body,
            HttpServletRequest request) {
        if (body.getReviewerId() == null || body.getReviewerId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reviewerId is required");
        }
        return Result.ok(
                courseInternalService.review(
                        courseId,
                        body.isApproved(),
                        body.getPriceFen(),
                        body.getRemark(),
                        body.getReviewerId()),
                TraceIds.resolve(request));
    }
}
