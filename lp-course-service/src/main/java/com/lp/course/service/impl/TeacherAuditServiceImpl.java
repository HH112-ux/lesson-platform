package com.lp.course.service.impl;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.dto.TeacherAuditApplicationView;
import com.lp.course.dto.TeacherAuditPageView;
import com.lp.course.dto.TeacherAuditResultView;
import com.lp.course.dto.TeacherAuditView;
import com.lp.course.entity.TeacherProfileAudit;
import com.lp.course.mapper.TeacherAuditMapper;
import com.lp.course.service.AdminAuditService;
import com.lp.course.service.TeacherAuditService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link TeacherAuditService} backed by {@link TeacherAuditMapper}.
 *
 * <p>A decision is applied with a conditional update on {@code audit_status = 0}, so two operators
 * deciding the same application at once cannot overwrite each other: the loser sees {@code 40901}.
 *
 * @author Caspian
 * @since 0.5.0
 */
@Service
public class TeacherAuditServiceImpl implements TeacherAuditService {

    /** Audit status of an application waiting for review. */
    private static final int AUDIT_PENDING = 0;

    /** Audit status of an approved teacher. */
    private static final int AUDIT_APPROVED = 1;

    /** Audit status of a rejected application. */
    private static final int AUDIT_REJECTED = 2;

    /** Maximum page size accepted by the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum remark length, matching {@code teacher_profile.audit_remark}. */
    private static final int MAX_REMARK_LENGTH = 300;

    private final TeacherAuditMapper teacherAuditMapper;

    private final AdminAuditService adminAuditService;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param teacherAuditMapper teacher profile audit statements
     * @param adminAuditService unified audit appender, joined to the review transaction
     * @author Caspian
     * @since 0.5.0
     */
    public TeacherAuditServiceImpl(
            TeacherAuditMapper teacherAuditMapper, AdminAuditService adminAuditService) {
        this.teacherAuditMapper = teacherAuditMapper;
        this.adminAuditService = adminAuditService;
    }

    /**
     * Lists one page of applications with the same filters used by the count statement.
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
    public TeacherAuditPageView page(
            Integer auditStatus, String keyword, int pageNum, int pageSize) {
        if (auditStatus != null && (auditStatus < AUDIT_PENDING || auditStatus > AUDIT_REJECTED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "auditStatus is out of range");
        }
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        long total = teacherAuditMapper.count(auditStatus, trimmedKeyword);
        if (total == 0) {
            return new TeacherAuditPageView(List.of(), 0, safePage, safeSize);
        }
        List<TeacherAuditView> records =
                teacherAuditMapper
                        .selectPage(
                                auditStatus,
                                trimmedKeyword,
                                (long) (safePage - 1) * safeSize,
                                safeSize)
                        .stream()
                        .map(this::toView)
                        .toList();
        return new TeacherAuditPageView(records, total, safePage, safeSize);
    }

    /**
     * Approves or rejects one pending application and keeps the decision on the profile row.
     *
     * @param teacherId profile primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @param traceId request trace id written to the unified audit log
     * @return application state after the decision
     * @throws BizException with {@code 40401} when the profile is absent, {@code 40901} when it is
     *     no longer pending, {@code 40001} when a rejection carries no remark
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    @Transactional
    public TeacherAuditResultView review(
            long teacherId,
            boolean approved,
            String remark,
            long reviewerId,
            int operatorRole,
            String traceId) {
        TeacherProfileAudit profile = teacherAuditMapper.selectById(teacherId);
        if (profile == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (profile.getAuditStatus() == null || profile.getAuditStatus() != AUDIT_PENDING) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "application is not pending");
        }
        String remarkText = null;
        if (!approved) {
            String trimmed = remark == null ? "" : remark.trim();
            if (trimmed.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "remark is required on rejection");
            }
            remarkText = trimmed.substring(0, Math.min(trimmed.length(), MAX_REMARK_LENGTH));
        }
        LocalDateTime auditedAt = LocalDateTime.now();
        int toStatus = approved ? AUDIT_APPROVED : AUDIT_REJECTED;
        if (teacherAuditMapper.applyReview(teacherId, toStatus, remarkText, reviewerId, auditedAt)
                == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "application is not pending");
        }
        // Same transaction as the conditional update: a rollback drops the audit row with it.
        adminAuditService.record(
                new AuditLogCreateRequest(
                        reviewerId,
                        operatorRole,
                        approved ? AuditAction.TEACHER_APPROVE : AuditAction.TEACHER_REJECT,
                        AuditObject.TEACHER,
                        teacherId,
                        AUDIT_PENDING,
                        toStatus,
                        remarkText,
                        traceId,
                        null,
                        auditedAt));
        return new TeacherAuditResultView(teacherId, toStatus, remarkText, reviewerId, auditedAt);
    }

    /**
     * Returns the caller's own application so the rejection reason can be displayed.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return the caller's application
     * @throws BizException with {@code 40301} when the caller is not a teacher or has no profile
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public TeacherAuditApplicationView application(long userId, String roles) {
        requireTeacherRole(roles);
        return toApplicationView(requireProfile(userId));
    }

    /**
     * Puts the caller's rejected application back into the review queue.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return the application after resubmission
     * @throws BizException with {@code 40301} when the caller has no profile, {@code 40901} when
     *     the profile is already approved
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public TeacherAuditApplicationView resubmit(long userId, String roles) {
        requireTeacherRole(roles);
        TeacherProfileAudit profile = requireProfile(userId);
        if (profile.getAuditStatus() != null && profile.getAuditStatus() == AUDIT_APPROVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "application is already approved");
        }
        if (teacherAuditMapper.resubmit(userId, LocalDateTime.now()) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "application cannot be resubmitted");
        }
        return toApplicationView(requireProfile(userId));
    }

    private TeacherProfileAudit requireProfile(long userId) {
        TeacherProfileAudit profile = teacherAuditMapper.selectByUserId(userId);
        if (profile == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        return profile;
    }

    private void requireTeacherRole(String roles) {
        if (roles == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        for (String role : roles.split(",")) {
            String trimmed = role.trim();
            if ("TEACHER".equals(trimmed) || "ADMIN".equals(trimmed)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private TeacherAuditView toView(TeacherProfileAudit row) {
        return new TeacherAuditView(
                row.getId(),
                row.getUserId(),
                row.getDisplayName(),
                row.getAvatarUrl(),
                row.getBio(),
                row.getStatus() == null ? 0 : row.getStatus(),
                row.getAuditStatus() == null ? AUDIT_PENDING : row.getAuditStatus(),
                row.getAuditRemark(),
                row.getAuditedBy(),
                row.getAuditedAt(),
                row.getSubmittedAt(),
                row.getCourseCount() == null ? 0L : row.getCourseCount(),
                row.getCreatedAt());
    }

    private TeacherAuditApplicationView toApplicationView(TeacherProfileAudit row) {
        return new TeacherAuditApplicationView(
                row.getId(),
                row.getAuditStatus() == null ? AUDIT_PENDING : row.getAuditStatus(),
                row.getAuditRemark(),
                row.getAuditFromStatus(),
                row.getAuditedBy(),
                row.getAuditedAt(),
                row.getSubmittedAt());
    }
}
