package com.lp.learn.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.learn.dto.CourseProgressSummaryView;
import com.lp.learn.service.MyCourseService;
import jakarta.servlet.http.HttpServletRequest;
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

    private final MyCourseService myCourseService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param myCourseService progress summary builder
     * @author Caspian
     * @since 0.1.0
     */
    public LearningInternalController(MyCourseService myCourseService) {
        this.myCourseService = myCourseService;
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
}
