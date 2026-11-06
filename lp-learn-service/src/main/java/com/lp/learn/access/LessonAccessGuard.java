package com.lp.learn.access;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.service.EntitlementService;
import org.springframework.stereotype.Component;

/**
 * Single place that decides whether a student may open a lesson.
 *
 * <p>Paid lessons always require a course entitlement. Free trial lessons bypass the check only
 * while {@code lp.learn.free-preview-enabled} is {@code true}; switching it off turns every lesson
 * into an entitlement-only lesson, which is the emergency brake for a free trial that should not be
 * public.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class LessonAccessGuard {

    private final EntitlementService entitlementService;

    private final LearnProperties learnProperties;

    /**
     * Creates the guard with its dependencies.
     *
     * @param entitlementService ownership check
     * @param learnProperties free preview switch
     * @author Caspian
     * @since 0.1.0
     */
    public LessonAccessGuard(
            EntitlementService entitlementService, LearnProperties learnProperties) {
        this.entitlementService = entitlementService;
        this.learnProperties = learnProperties;
    }

    /**
     * Ensures the student may play or report progress for the lesson.
     *
     * @param userId student user id
     * @param lesson lesson metadata including the free trial flag
     * @throws BizException with {@code 40302} when the lesson needs an entitlement the user lacks
     * @author Caspian
     * @since 0.1.0
     */
    public void requireAccess(long userId, LessonPlayInfo lesson) {
        if (isPreviewAllowed(lesson)
                || entitlementService.hasEntitlement(userId, lesson.getCourseId())) {
            return;
        }
        throw new BizException(ErrorCode.LEARN_NOT_ENTITLED);
    }

    /**
     * Ensures the student owns the course; used by course level aggregates that have no lesson.
     *
     * @param userId student user id
     * @param courseId course id
     * @throws BizException with {@code 40302} when the user has no entitlement
     * @author Caspian
     * @since 0.1.0
     */
    public void requireCourseAccess(long userId, long courseId) {
        if (!entitlementService.hasEntitlement(userId, courseId)) {
            throw new BizException(ErrorCode.LEARN_NOT_ENTITLED);
        }
    }

    /**
     * Tells whether the free trial flag currently grants access without purchase.
     *
     * @param lesson lesson metadata
     * @return {@code true} only for a free lesson while the preview switch is on
     * @author Caspian
     * @since 0.1.0
     */
    boolean isPreviewAllowed(LessonPlayInfo lesson) {
        return learnProperties.isFreePreviewEnabled() && lesson.isFree();
    }
}
