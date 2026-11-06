package com.lp.course.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.service.CoursePublicService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public course endpoints routed through the gateway.
 *
 * <p>Unlike {@code /internal/v1/**} these routes require a gateway access token and no internal
 * token header; they expose only buyer visible fields.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/courses")
public class CoursePublicController {

    private final CoursePublicService coursePublicService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param coursePublicService public course queries
     * @author Caspian
     * @since 0.1.0
     */
    public CoursePublicController(CoursePublicService coursePublicService) {
        this.coursePublicService = coursePublicService;
    }

    /**
     * Returns the detail shown on the purchase page.
     *
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return public course detail, {@code 40401} when absent; offline courses return {@code
     *     status=3}
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}")
    public Result<PublicCourseView> detail(
            @PathVariable("courseId") long courseId, HttpServletRequest request) {
        return Result.ok(coursePublicService.course(courseId), TraceIds.resolve(request));
    }
}
