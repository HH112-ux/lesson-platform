package com.lp.admin.controller;

import com.lp.admin.dto.AdminCourseReviewAuditView;
import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;
import com.lp.admin.dto.ReviewCourseRequest;
import com.lp.admin.service.AdminCourseReviewService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations course review endpoints.
 *
 * <p>The gateway authenticates the caller and injects the roles; this controller only accepts
 * {@code ADMIN} and {@code OPS}. Review decisions are forwarded to course-service, which owns the
 * course state machine.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/admin/courses")
public class AdminCourseController {

    private final AdminCourseReviewService adminCourseReviewService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param adminCourseReviewService review use cases
     * @author Caspian
     * @since 0.1.0
     */
    public AdminCourseController(AdminCourseReviewService adminCourseReviewService) {
        this.adminCourseReviewService = adminCourseReviewService;
    }

    /**
     * Lists the courses waiting for review.
     *
     * @param roles gateway injected roles, comma separated
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of pending courses
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/pending")
    public Result<AdminPendingCoursePageView> pending(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireReviewer(roles);
        return Result.ok(
                adminCourseReviewService.pending(pageNum, pageSize), TraceIds.resolve(request));
    }

    /**
     * Approves or rejects one pending course.
     *
     * @param userId gateway injected operator id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param body decision, optional price in fen and the rejection remark
     * @param request current request, used to resolve the trace id
     * @return course state after the decision
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{courseId}/review")
    public Result<AdminReviewResultView> review(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @RequestBody ReviewCourseRequest body,
            HttpServletRequest request) {
        requireReviewer(roles);
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return Result.ok(
                adminCourseReviewService.review(
                        courseId, body.isApproved(), body.getPriceFen(), body.getRemark(), userId),
                TraceIds.resolve(request));
    }

    /**
     * Lists the review audit history of one course.
     *
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return audit entries, newest decision first
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}/review-audits")
    public Result<List<AdminCourseReviewAuditView>> reviewAudits(
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            HttpServletRequest request) {
        requireReviewer(roles);
        return Result.ok(
                adminCourseReviewService.reviewAudits(courseId), TraceIds.resolve(request));
    }

    private void requireReviewer(String roles) {
        boolean allowed =
                roles != null
                        && Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .anyMatch(role -> "ADMIN".equals(role) || "OPS".equals(role));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
