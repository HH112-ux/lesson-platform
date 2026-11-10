package com.lp.course.mapper;

import com.lp.course.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * Appends unified operations audit entries.
 *
 * <p>The table is append-only: no update or delete statement is exposed on purpose. The insert uses
 * {@code ON DUPLICATE KEY UPDATE dedupe_key = dedupe_key} so a redelivery from the trade-service
 * audit outbox is absorbed by the unique key instead of raising an error.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Mapper
public interface AdminAuditLogMapper {

    /**
     * Appends one audit entry.
     *
     * @param audit audit row to insert, filled with the generated id when inserted
     * @return affected rows, {@code 0} when a redelivery hit the dedupe unique key
     * @author Caspian
     * @since 0.6.0
     */
    @Insert(
            "INSERT INTO admin_audit_log (operator_id, operator_role, action_type, object_type, "
                    + "object_id, from_status, to_status, remark, trace_id, dedupe_key, created_at) "
                    + "VALUES (#{operatorId}, #{operatorRole}, #{actionType}, #{objectType}, "
                    + "#{objectId}, #{fromStatus}, #{toStatus}, #{remark}, #{traceId}, "
                    + "#{dedupeKey}, #{createdAt}) "
                    + "ON DUPLICATE KEY UPDATE dedupe_key = dedupe_key")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AdminAuditLog audit);
}
