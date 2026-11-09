package com.lp.course.mapper;

import com.lp.course.entity.AdminAuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Appends and reads unified operations audit entries.
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

    /** Column list shared by the read statements. */
    String COLUMNS =
            "id, operator_id AS operatorId, operator_role AS operatorRole, "
                    + "action_type AS actionType, object_type AS objectType, "
                    + "object_id AS objectId, from_status AS fromStatus, to_status AS toStatus, "
                    + "remark, trace_id AS traceId, dedupe_key AS dedupeKey, "
                    + "created_at AS createdAt";

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

    /**
     * Counts the audit entries of one object.
     *
     * @param objectType object type code
     * @param objectId object primary key
     * @return matching row count
     * @author Caspian
     * @since 0.6.0
     */
    @Select(
            "SELECT COUNT(*) FROM admin_audit_log "
                    + "WHERE object_type = #{objectType} AND object_id = #{objectId}")
    long countByObject(@Param("objectType") int objectType, @Param("objectId") long objectId);

    /**
     * Lists the audit entries of one object, newest decision first.
     *
     * @param objectType object type code
     * @param objectId object primary key
     * @param offset zero based row offset
     * @param limit page size
     * @return matching rows, possibly empty
     * @author Caspian
     * @since 0.6.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM admin_audit_log WHERE object_type = #{objectType} "
                    + "AND object_id = #{objectId} ORDER BY created_at DESC, id DESC "
                    + "LIMIT #{limit} OFFSET #{offset}")
    List<AdminAuditLog> selectByObject(
            @Param("objectType") int objectType,
            @Param("objectId") long objectId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * Counts the audit entries of one operator inside a half-open time range.
     *
     * @param operatorId operator user id
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @return matching row count
     * @author Caspian
     * @since 0.6.0
     */
    @Select(
            "SELECT COUNT(*) FROM admin_audit_log WHERE operator_id = #{operatorId} "
                    + "AND created_at >= #{from} AND created_at < #{to}")
    long countByOperator(
            @Param("operatorId") long operatorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Lists the audit entries of one operator inside a half-open time range, newest first.
     *
     * @param operatorId operator user id
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @param offset zero based row offset
     * @param limit page size
     * @return matching rows, possibly empty
     * @author Caspian
     * @since 0.6.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM admin_audit_log WHERE operator_id = #{operatorId} "
                    + "AND created_at >= #{from} AND created_at < #{to} "
                    + "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<AdminAuditLog> selectByOperator(
            @Param("operatorId") long operatorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
