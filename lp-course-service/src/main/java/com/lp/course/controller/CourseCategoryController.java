package com.lp.course.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.CourseCategoryView;
import com.lp.course.service.CoursePublicService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public taxonomy endpoint used by the storefront filter bar.
 *
 * <p>Anonymous friendly (gateway optional-auth) and independent from the internal API.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CourseCategoryController {

    private final CoursePublicService coursePublicService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param coursePublicService public category queries
     * @author Caspian
     * @since 0.1.0
     */
    public CourseCategoryController(CoursePublicService coursePublicService) {
        this.coursePublicService = coursePublicService;
    }

    /**
     * Lists the visible categories.
     *
     * @param request current request, used to resolve the trace id
     * @return categories in display order
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<List<CourseCategoryView>> categories(HttpServletRequest request) {
        return Result.ok(coursePublicService.categories(), TraceIds.resolve(request));
    }
}
