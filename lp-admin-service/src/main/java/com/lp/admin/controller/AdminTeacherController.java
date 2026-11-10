package com.lp.admin.controller;

import com.lp.admin.dto.AdminTeacherPageView;
import com.lp.admin.dto.AdminTeacherReviewRequest;
import com.lp.admin.dto.AdminTeacherReviewResultView;
import com.lp.admin.service.AdminTeacherAuditService;
import com.lp.admin.support.ConsoleRoles;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations teacher onboarding review endpoints.
 *
 * <p>Access needs both an operations role and the {@code admin:teacher:audit} permission code,
 * which is the same code the console menu node declares. Decisions are forwarded to course-service,
 * which owns the teacher audit columns.
 *
 * @author Caspian
 * @since 0.5.0
 */
@RestController
@RequestMapping("/api/v1/admin/teachers")
public class AdminTeacherController {

    private final AdminTeacherAuditService adminTeacherAuditService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminTeacherAuditService onboarding review use cases
     * @author Caspian
     * @since 0.5.0
     */
    public AdminTeacherController(AdminTeacherAuditService adminTeacherAuditService) {
        this.adminTeacherAuditService = adminTeacherAuditService;
    }

    /**
     * Lists teacher applications with an optional status filter.
     *
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
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
    public Result<AdminTeacherPageView> list(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireAuditor(roles, permissions);
        return Result.ok(
                adminTeacherAuditService.page(auditStatus, keyword, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Approves or rejects one pending application.
     *
     * @param userId gateway injected operator id
     * @param roles gateway injected roles, comma separated
     * @param permissions gateway injected permission codes, comma separated
     * @param teacherId profile primary key
     * @param body decision and the rejection remark
     * @param request current request, used to resolve the trace id
     * @return application state after the decision
     * @author Caspian
     * @since 0.5.0
     */
    @PostMapping("/{teacherId}/review")
    public Result<AdminTeacherReviewResultView> review(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestHeader(value = IdentityHeaders.USER_PERMISSIONS, required = false)
                    String permissions,
            @PathVariable("teacherId") long teacherId,
            @RequestBody AdminTeacherReviewRequest body,
            HttpServletRequest request) {
        requireAuditor(roles, permissions);
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        int operatorRole = ConsoleRoles.operatorRole(roles);
        return Result.ok(
                adminTeacherAuditService.review(
                        teacherId, body.approved(), body.remark(), userId, operatorRole),
                TraceIds.resolve(request));
    }

    private void requireAuditor(String roles, String permissions) {
        ConsoleRoles.requireConsole(roles);
        ConsoleRoles.requirePermission(permissions, ConsoleRoles.TEACHER_AUDIT);
    }
}
