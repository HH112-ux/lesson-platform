package com.lp.learn.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.learn.dto.PlayTokenView;
import com.lp.learn.service.PlayTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lesson playback endpoints.
 *
 * <p>Route prefix {@code /api/v1/lessons} is the documented one; the gateway forwards it to
 * learn-service. The student id always comes from the gateway injected header.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/lessons")
public class LessonController {

    private final PlayTokenService playTokenService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param playTokenService playback signing service
     * @author Caspian
     * @since 0.1.0
     */
    public LessonController(PlayTokenService playTokenService) {
        this.playTokenService = playTokenService;
    }

    /**
     * Issues a playback URL for one lesson.
     *
     * @param lessonId lesson primary key
     * @param userId gateway injected user id
     * @param request current request, used to resolve the trace id
     * @return presigned URL and its expiry
     * @throws BizException with {@code 40101}, {@code 40401}, {@code 40302} or {@code 50206}
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{lessonId}/play-token")
    public Result<PlayTokenView> playToken(
            @PathVariable("lessonId") long lessonId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(playTokenService.issue(userId, lessonId), TraceIds.resolve(request));
    }
}
