package com.lp.admin.service;

import com.lp.admin.client.AdminAuditClient;
import com.lp.admin.dto.AdminAuditLogPageView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;

/**
 * Console facing use case for the unified operations audit log.
 *
 * <p>The time strings arrive from query parameters, so they are parsed here and a malformed value
 * becomes {@code 40001} instead of a framework level conversion failure. Paging is clamped before
 * the upstream call.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Service
public class AdminAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditClient adminAuditClient;

    /**
     * Creates the service with its course-service port.
     *
     * @param adminAuditClient unified audit query API
     * @author Caspian
     * @since 0.6.0
     */
    public AdminAuditQueryService(AdminAuditClient adminAuditClient) {
        this.adminAuditClient = adminAuditClient;
    }

    /**
     * Reads one page in object mode or operator plus time range mode.
     *
     * @param objectType object type code, paired with {@code objectId}
     * @param objectId object primary key, paired with {@code objectType}
     * @param operatorId operator user id, used when object mode is absent
     * @param from inclusive lower bound in ISO local date-time form, used in operator mode
     * @param to exclusive upper bound in ISO local date-time form, used in operator mode
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of audit entries
     * @throws BizException with {@code 40001} when a time value is not ISO local date-time
     * @author Caspian
     * @since 0.6.0
     */
    public AdminAuditLogPageView page(
            Integer objectType,
            Long objectId,
            Long operatorId,
            String from,
            String to,
            int pageNum,
            int pageSize) {
        LocalDateTime fromTime = parseTime(from, "from");
        LocalDateTime toTime = parseTime(to, "to");
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return adminAuditClient.page(
                objectType, objectId, operatorId, fromTime, toTime, safePage, safeSize);
    }

    private static LocalDateTime parseTime(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    name + " must be an ISO local date-time such as 2026-11-09T00:00:00");
        }
    }
}
