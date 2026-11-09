package com.lp.admin.service.impl;

import com.lp.admin.client.TeacherAuditClient;
import com.lp.admin.dto.AdminTeacherPageView;
import com.lp.admin.dto.AdminTeacherReviewResultView;
import com.lp.admin.service.AdminTeacherAuditService;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Validates console input before forwarding teacher onboarding work to course-service.
 *
 * @author Caspian
 * @since 0.5.0
 */
@Service
public class AdminTeacherAuditServiceImpl implements AdminTeacherAuditService {

    /** Audit status of an application waiting for review. */
    private static final int AUDIT_PENDING = 0;

    /** Audit status of a rejected application. */
    private static final int AUDIT_REJECTED = 2;

    /** Maximum page size accepted by the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum remark length, matching {@code teacher_profile.audit_remark}. */
    private static final int MAX_REMARK_LENGTH = 300;

    private final TeacherAuditClient teacherAuditClient;

    /**
     * Creates the service with its client dependency.
     *
     * @param teacherAuditClient course-service onboarding port
     * @author Caspian
     * @since 0.5.0
     */
    public AdminTeacherAuditServiceImpl(TeacherAuditClient teacherAuditClient) {
        this.teacherAuditClient = teacherAuditClient;
    }

    /**
     * Lists one page of applications after normalizing the filters.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of applications
     * @throws BizException with {@code 40001} when the status filter is out of range
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public AdminTeacherPageView page(
            Integer auditStatus, String keyword, int pageNum, int pageSize) {
        if (auditStatus != null && (auditStatus < AUDIT_PENDING || auditStatus > AUDIT_REJECTED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "auditStatus is out of range");
        }
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String trimmedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return teacherAuditClient.page(auditStatus, trimmedKeyword, safePage, safeSize);
    }

    /**
     * Approves or rejects one pending application.
     *
     * @param teacherId profile primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return application state after the decision
     * @throws BizException with {@code 40001} when a rejection carries no remark
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public AdminTeacherReviewResultView review(
            long teacherId, boolean approved, String remark, long reviewerId, int operatorRole) {
        String remarkText = null;
        if (!approved) {
            String trimmed = remark == null ? "" : remark.trim();
            if (trimmed.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "remark is required on rejection");
            }
            remarkText = trimmed.substring(0, Math.min(trimmed.length(), MAX_REMARK_LENGTH));
        }
        return teacherAuditClient.review(teacherId, approved, remarkText, reviewerId, operatorRole);
    }
}
