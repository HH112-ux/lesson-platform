package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.service.CourseInternalService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
