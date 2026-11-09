package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.TeacherAuditApplicationView;
import com.lp.course.service.TeacherAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher onboarding application endpoints.
 *
 * <p>A teacher reads the status and rejection reason of the caller's own application and puts a
 * rejected application back into the review queue. Approval is granted only by operations.
 *
 * @author Caspian
 * @since 0.5.0
 */
@RestController
@RequestMapping("/api/v1/teacher/audit-application")
public class TeacherAuditController {

    private final TeacherAuditService teacherAuditService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param teacherAuditService onboarding review use cases
     * @author Caspian
     * @since 0.5.0
     */
    public TeacherAuditController(TeacherAuditService teacherAuditService) {
        this.teacherAuditService = teacherAuditService;
    }

    /**
     * Returns the caller's own application and its rejection reason.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param request current request, used to resolve the trace id
     * @return the caller's application
     * @author Caspian
     * @since 0.5.0
     */
    @GetMapping
    public Result<TeacherAuditApplicationView> application(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            HttpServletRequest request) {
        return Result.ok(
                teacherAuditService.application(requireUser(userId), roles),
                TraceIds.resolve(request));
    }

    /**
     * Resubmits a rejected application for review.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param request current request, used to resolve the trace id
     * @return the application after resubmission
     * @author Caspian
     * @since 0.5.0
     */
    @PostMapping
    public Result<TeacherAuditApplicationView> resubmit(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            HttpServletRequest request) {
        return Result.ok(
                teacherAuditService.resubmit(requireUser(userId), roles),
                TraceIds.resolve(request));
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
