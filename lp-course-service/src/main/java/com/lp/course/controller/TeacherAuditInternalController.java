package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.TeacherAuditPageView;
import com.lp.course.dto.TeacherAuditResultView;
import com.lp.course.dto.TeacherAuditReviewRequest;
import com.lp.course.service.TeacherAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service teacher onboarding review endpoints.
 *
 * <p>The {@code /internal/v1} prefix is neither routed nor whitelisted by the gateway, so the queue
 * and the decision are reachable only from admin-service behind {@code X-Internal-Token}.
 *
 * @author Caspian
 * @since 0.5.0
 */
@RestController
@RequestMapping("/internal/v1/teachers")
public class TeacherAuditInternalController {

    private final TeacherAuditService teacherAuditService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param teacherAuditService onboarding review use cases
     * @author Caspian
     * @since 0.5.0
     */
    public TeacherAuditInternalController(TeacherAuditService teacherAuditService) {
        this.teacherAuditService = teacherAuditService;
    }

    /**
     * Lists teacher applications with an optional status filter.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of applications
     * @author Caspian
     * @since 0.5.0
     */
    @GetMapping
    public Result<TeacherAuditPageView> list(
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                teacherAuditService.page(auditStatus, keyword, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Applies an approval or rejection to one pending application.
     *
     * @param teacherId profile primary key
     * @param body decision, optional remark and the operator id
     * @param request current request, used to resolve the trace id
     * @return application state after the decision
     * @throws BizException with {@code 40001} when the operator id is missing
     * @author Caspian
     * @since 0.5.0
     */
    @PostMapping("/{teacherId}/review")
    public Result<TeacherAuditResultView> review(
            @PathVariable("teacherId") long teacherId,
            @RequestBody TeacherAuditReviewRequest body,
            HttpServletRequest request) {
        if (body.reviewerId() == null || body.reviewerId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reviewerId is required");
        }
        return Result.ok(
                teacherAuditService.review(
                        teacherId, body.approved(), body.remark(), body.reviewerId()),
                TraceIds.resolve(request));
    }
}
