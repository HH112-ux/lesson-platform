package com.lp.course.controller;

import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.CoursePageView;
import com.lp.course.dto.CourseSyllabusView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.service.CoursePublicService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public course endpoints routed through the gateway.
 *
 * <p>These routes accept anonymous callers: the gateway treats them as optional-auth, so a missing
 * token is not an error while a valid token still fills the owned flag. They are not part of {@code
 * /internal/v1/**} and never require {@code X-Internal-Token}.
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
     * Returns one page of published courses.
     *
     * @param category category id or slug, optional
     * @param keyword title/subtitle fragment, optional
     * @param page one based page number, defaults to 1
     * @param size page size, defaults to 20 and is capped at 100
     * @param userId gateway injected user id, absent when anonymous
     * @param request current request, used to resolve the trace id
     * @return published course page
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<CoursePageView> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        return Result.ok(
                coursePublicService.list(category, keyword, page, size, userId),
                TraceIds.resolve(request));
    }

    /**
     * Returns the detail shown on the purchase page.
     *
     * @param courseId course primary key
     * @param userId gateway injected user id, absent when anonymous
     * @param request current request, used to resolve the trace id
     * @return public course detail; {@code 40401} when the course is absent, not published, or
     *     offline and not owned by the caller
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}")
    public Result<PublicCourseView> detail(
            @PathVariable("courseId") long courseId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        return Result.ok(coursePublicService.course(courseId, userId), TraceIds.resolve(request));
    }

    /**
     * Returns the chapter and lesson outline used by the course detail page.
     *
     * @param courseId course primary key
     * @param userId gateway injected user id, absent when anonymous
     * @param request current request, used to resolve the trace id
     * @return syllabus; {@code 40401} when the course is absent, not published, or offline and not
     *     owned by the caller
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}/chapters")
    public Result<CourseSyllabusView> chapters(
            @PathVariable("courseId") long courseId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        return Result.ok(coursePublicService.syllabus(courseId, userId), TraceIds.resolve(request));
    }
}
