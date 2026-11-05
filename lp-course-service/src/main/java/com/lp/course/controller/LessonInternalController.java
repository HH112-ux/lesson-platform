package com.lp.course.controller;

import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.LessonPlayView;
import com.lp.course.service.CourseInternalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service lesson queries.
 *
 * <p>Only learn-service calls this route; the gateway never forwards {@code /internal/v1/**}.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/internal/v1/lessons")
public class LessonInternalController {

    private final CourseInternalService courseInternalService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param courseInternalService internal course queries
     * @author Caspian
     * @since 0.1.0
     */
    public LessonInternalController(CourseInternalService courseInternalService) {
        this.courseInternalService = courseInternalService;
    }

    /**
     * Returns the media projection of one playable lesson.
     *
     * @param lessonId lesson primary key
     * @param request current request, used to resolve the trace id
     * @return lesson media projection, {@code 40401} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{lessonId}")
    public Result<LessonPlayView> play(
            @PathVariable("lessonId") long lessonId, HttpServletRequest request) {
        return Result.ok(courseInternalService.lessonForPlay(lessonId), TraceIds.resolve(request));
    }
}
