package com.lp.learn.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.learn.dto.CourseProgressView;
import com.lp.learn.dto.LessonProgressView;
import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;
import com.lp.learn.dto.ReportProgressRequest;
import com.lp.learn.service.LearningProgressService;
import com.lp.learn.service.MyCourseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student facing learning endpoints.
 *
 * <p>The student id comes from the gateway injected header; the body never carries a user id, so a
 * client cannot read another student's courses.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final MyCourseService myCourseService;

    private final LearningProgressService learningProgressService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param myCourseService my-courses builder
     * @param learningProgressService progress recorder
     * @author Caspian
     * @since 0.1.0
     */
    public LearningController(
            MyCourseService myCourseService, LearningProgressService learningProgressService) {
        this.myCourseService = myCourseService;
        this.learningProgressService = learningProgressService;
    }

    /**
     * Returns the courses the student may access, newest entitlement first.
     *
     * @param userId gateway injected user id, may be absent without a token
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return paged course list
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/my-courses")
    public Result<PageView<MyCourseView>> myCourses(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(
                myCourseService.myCourses(userId, pageNum, pageSize), TraceIds.resolve(request));
    }

    /**
     * Reports a playback position; completion is recomputed on the server.
     *
     * @param body lesson id and position, never a completion flag
     * @param userId gateway injected user id
     * @param request current request, used to resolve the trace id
     * @return stored progress
     * @throws BizException with {@code 40101}, {@code 40401}, {@code 40302} or {@code 63001}
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/progress")
    public Result<LessonProgressView> reportProgress(
            @RequestBody(required = false) ReportProgressRequest body,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (body == null || body.getLessonId() == null || body.getPositionSeconds() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "lessonId and position are required");
        }
        return Result.ok(
                learningProgressService.report(
                        userId, body.getLessonId(), body.getPositionSeconds()),
                TraceIds.resolve(request));
    }

    /**
     * Returns the student's progress for one course.
     *
     * @param courseId course id
     * @param userId gateway injected user id
     * @param request current request, used to resolve the trace id
     * @return reported lessons
     * @throws BizException with {@code 40101} or {@code 40302}
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/progress")
    public Result<CourseProgressView> courseProgress(
            @RequestParam("courseId") long courseId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(
                learningProgressService.courseProgress(userId, courseId),
                TraceIds.resolve(request));
    }
}
