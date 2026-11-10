package com.lp.admin.client;

import com.lp.admin.dto.AdminAuditLogPageView;
import java.time.LocalDateTime;

/**
 * Port to the course-service unified audit query API.
 *
 * @author Caspian
 * @since 0.6.0
 */
public interface AdminAuditClient {

    /**
     * Reads one page in object mode or operator plus time range mode.
     *
     * @param objectType object type code, paired with {@code objectId}
     * @param objectId object primary key, paired with {@code objectType}
     * @param operatorId operator user id, used when object mode is absent
     * @param from inclusive lower bound, used in operator mode
     * @param to exclusive upper bound, used in operator mode
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of audit entries
     * @author Caspian
     * @since 0.6.0
     */
    AdminAuditLogPageView page(
            Integer objectType,
            Long objectId,
            Long operatorId,
            LocalDateTime from,
            LocalDateTime to,
            int pageNum,
            int pageSize);
}
