package com.lp.course.service;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.dto.AuditLogPageView;
import com.lp.course.dto.AuditLogView;
import com.lp.course.entity.AdminAuditLog;
import com.lp.course.mapper.AdminAuditLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Writes and reads the unified operations audit log.
 *
 * <p>{@link #record(AuditLogCreateRequest)} only appends one row and never opens a transaction of
 * its own, so the caller decides the boundary: course and teacher reviews call it inside their own
 * {@code @Transactional} methods, and the internal endpoint called by the trade-service relay calls
 * it in a fresh transaction. Keeping the write in the business transaction is what prevents the two
 * failure modes "status changed without an audit row" and "audit row written for a change that
 * rolled back".
 *
 * <p>The query API supports exactly two retrieval shapes: by object (type and id) and by operator
 * inside a half-open time range {@code [from, to)}. Requiring one of them keeps an unfiltered scan
 * of the whole log unreachable through the console.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Service
public class AdminAuditService {

    /** Maximum page size accepted by the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum remark length, matching {@code admin_audit_log.remark}. */
    private static final int MAX_REMARK_LENGTH = 300;

    /** Maximum trace id length, matching {@code admin_audit_log.trace_id}. */
    private static final int MAX_TRACE_LENGTH = 64;

    /** Maximum dedupe key length, matching {@code admin_audit_log.dedupe_key}. */
    private static final int MAX_DEDUPE_LENGTH = 128;

    /** Lowest action code, {@link AuditAction#COURSE_APPROVE}. */
    private static final int ACTION_MIN = AuditAction.COURSE_APPROVE;

    /** Highest action code, {@link AuditAction#SETTLE_VOID}. */
    private static final int ACTION_MAX = AuditAction.SETTLE_VOID;

    /** Lowest object type code, {@link AuditObject#COURSE}. */
    private static final int OBJECT_MIN = AuditObject.COURSE;

    /** Highest object type code, {@link AuditObject#SETTLE_BATCH}. */
    private static final int OBJECT_MAX = AuditObject.SETTLE_BATCH;

    private final AdminAuditLogMapper adminAuditLogMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param adminAuditLogMapper append and read statements of {@code admin_audit_log}
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

    /**
     * Reads one page in object mode or operator mode.
     *
     * @param objectType object type code, required together with {@code objectId}
     * @param objectId object primary key, required together with {@code objectType}
     * @param operatorId operator user id, required when object mode is not used
     * @param from inclusive lower bound, required in operator mode
     * @param to exclusive upper bound, required in operator mode
     * @param pageNum one based page number
     * @param pageSize page size, capped at 100
     * @return one page of audit entries, newest decision first
     * @throws BizException with {@code 40001} when neither retrieval shape is complete
     * @author Caspian
     * @since 0.6.0
     */
    public AuditLogPageView page(
            Integer objectType,
            Long objectId,
            Long operatorId,
            LocalDateTime from,
            LocalDateTime to,
            int pageNum,
            int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (safePage - 1) * safeSize;
        if (objectType != null || objectId != null) {
            if (objectType == null || objectId == null) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID, "objectType and objectId must be paired");
            }
            if (objectType < OBJECT_MIN || objectType > OBJECT_MAX) {
                throw new BizException(ErrorCode.PARAM_INVALID, "objectType is invalid");
            }
            if (objectId <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "objectId is invalid");
            }
            long total = adminAuditLogMapper.countByObject(objectType, objectId);
            if (total == 0) {
                return new AuditLogPageView(List.of(), 0, safePage, safeSize);
            }
            List<AuditLogView> records =
                    adminAuditLogMapper
                            .selectByObject(objectType, objectId, offset, safeSize)
                            .stream()
                            .map(AdminAuditService::toView)
                            .toList();
            return new AuditLogPageView(records, total, safePage, safeSize);
        }
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "either objectType with objectId, or operatorId with from and to, is required");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "from and to are required and from must precede to");
        }
        long total = adminAuditLogMapper.countByOperator(operatorId, from, to);
        if (total == 0) {
            return new AuditLogPageView(List.of(), 0, safePage, safeSize);
        }
        List<AuditLogView> records =
                adminAuditLogMapper
                        .selectByOperator(operatorId, from, to, offset, safeSize)
                        .stream()
                        .map(AdminAuditService::toView)
                        .toList();
        return new AuditLogPageView(records, total, safePage, safeSize);
    }

    private static AuditLogView toView(AdminAuditLog row) {
        return new AuditLogView(
                row.getId(),
                row.getOperatorId(),
                row.getOperatorRole(),
                row.getActionType(),
                row.getObjectType(),
                row.getObjectId(),
                row.getFromStatus(),
                row.getToStatus(),
                row.getRemark(),
                row.getTraceId(),
                row.getCreatedAt());
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
