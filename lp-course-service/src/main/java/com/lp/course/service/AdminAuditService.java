package com.lp.course.service;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.entity.AdminAuditLog;
import com.lp.course.mapper.AdminAuditLogMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Writes the unified operations audit log.
 *
 * <p>{@link #record(AuditLogCreateRequest)} only appends one row and never opens a transaction of
 * its own, so the caller decides the boundary: course and teacher reviews call it inside their own
 * {@code @Transactional} methods, and the internal endpoint called by the trade-service relay calls
 * it in a fresh transaction. Keeping the write in the business transaction is what prevents the two
 * failure modes "status changed without an audit row" and "audit row written for a change that
 * rolled back".
 *
 * @author Caspian
 * @since 0.6.0
 */
@Service
public class AdminAuditService {

    /** Maximum remark length, matching {@code admin_audit_log.remark}. */
    private static final int MAX_REMARK_LENGTH = 300;

    /** Maximum trace id length, matching {@code admin_audit_log.trace_id}. */
    private static final int MAX_TRACE_LENGTH = 64;

    /** Maximum dedupe key length, matching {@code admin_audit_log.dedupe_key}. */
    private static final int MAX_DEDUPE_LENGTH = 128;

    /** Lowest action code, {@link AuditAction#COURSE_APPROVE}. */
    private static final int ACTION_MIN = AuditAction.COURSE_APPROVE;

    /** Highest action code, {@link AuditAction#TEACHER_REJECT}. */
    private static final int ACTION_MAX = AuditAction.TEACHER_REJECT;

    /** Lowest object type code, {@link AuditObject#COURSE}. */
    private static final int OBJECT_MIN = AuditObject.COURSE;

    /** Highest object type code, {@link AuditObject#TEACHER}. */
    private static final int OBJECT_MAX = AuditObject.TEACHER;

    private final AdminAuditLogMapper adminAuditLogMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param adminAuditLogMapper append statements of {@code admin_audit_log}
     * @author Caspian
     * @since 0.6.0
     */
    public AdminAuditService(AdminAuditLogMapper adminAuditLogMapper) {
        this.adminAuditLogMapper = adminAuditLogMapper;
    }

    /**
     * Appends one audit entry after validating the shared codes.
     *
     * @param request audit entry to append
     * @throws BizException with {@code 40001} when a required value is missing or a code is unknown
     * @author Caspian
     * @since 0.6.0
     */
    public void record(AuditLogCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "audit entry is required");
        }
        if (request.operatorId() == null || request.operatorId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "operatorId is required");
        }
        if (!OperatorRole.isValid(request.operatorRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "operatorRole is invalid");
        }
        if (request.actionType() == null
                || request.actionType() < ACTION_MIN
                || request.actionType() > ACTION_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "actionType is invalid");
        }
        if (request.objectType() == null
                || request.objectType() < OBJECT_MIN
                || request.objectType() > OBJECT_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "objectType is invalid");
        }
        if (request.objectId() == null || request.objectId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "objectId is required");
        }
        if (request.fromStatus() == null || request.toStatus() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fromStatus and toStatus are required");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "traceId is required");
        }
        AdminAuditLog row = new AdminAuditLog();
        row.setOperatorId(request.operatorId());
        row.setOperatorRole(request.operatorRole());
        row.setActionType(request.actionType());
        row.setObjectType(request.objectType());
        row.setObjectId(request.objectId());
        row.setFromStatus(request.fromStatus());
        row.setToStatus(request.toStatus());
        row.setRemark(truncate(request.remark(), MAX_REMARK_LENGTH));
        row.setTraceId(truncate(request.traceId().trim(), MAX_TRACE_LENGTH));
        row.setDedupeKey(blankToNull(truncate(request.dedupeKey(), MAX_DEDUPE_LENGTH)));
        row.setCreatedAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        adminAuditLogMapper.insert(row);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
                ? null
                : trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
