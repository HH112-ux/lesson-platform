package com.lp.trade.mapper;

import com.lp.trade.entity.AuditOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code audit_outbox}.
 *
 * <p>The insert runs in the same transaction as the refund status update. Its unique key on {@code
 * dedupe_key} keeps the write idempotent, and the same key is sent to course-service so a delivery
 * retry cannot duplicate the audit row.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Mapper
public interface AuditOutboxMapper {

    /**
     * Inserts one delivery intent and fills the generated id.
     *
     * @param outbox intent row
     * @return affected rows
     * @author Caspian
     * @since 0.6.0
     */
    @Insert(
            "INSERT INTO audit_outbox (dedupe_key, operator_id, operator_role, action_type, "
                    + "object_type, object_id, from_status, to_status, remark, trace_id, "
                    + "occurred_at, status, retry_count, next_retry_at) VALUES (#{dedupeKey}, "
                    + "#{operatorId}, #{operatorRole}, #{actionType}, #{objectType}, #{objectId}, "
                    + "#{fromStatus}, #{toStatus}, #{remark}, #{traceId}, #{occurredAt}, 0, 0, "
                    + "CURRENT_TIMESTAMP(3))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditOutbox outbox);

    /**
     * Loads pending intents whose retry time has arrived, oldest first.
     *
     * @param limit maximum rows
     * @return pending intents, possibly empty
     * @author Caspian
     * @since 0.6.0
     */
    @Select(
            "SELECT id, dedupe_key AS dedupeKey, operator_id AS operatorId, "
                    + "operator_role AS operatorRole, action_type AS actionType, "
                    + "object_type AS objectType, object_id AS objectId, "
                    + "from_status AS fromStatus, to_status AS toStatus, remark, "
                    + "trace_id AS traceId, occurred_at AS occurredAt, status, "
                    + "retry_count AS retryCount, next_retry_at AS nextRetryAt, "
                    + "delivered_at AS deliveredAt, last_error AS lastError "
                    + "FROM audit_outbox WHERE status = 0 "
                    + "AND next_retry_at <= CURRENT_TIMESTAMP(3) ORDER BY id LIMIT #{limit}")
    List<AuditOutbox> selectPending(@Param("limit") int limit);

    /**
     * Marks one intent as delivered.
     *
     * @param id intent id
     * @param deliveredAt delivery time
     * @return affected rows, {@code 0} when another relay already finished it
     * @author Caspian
     * @since 0.6.0
     */
    @Update(
            "UPDATE audit_outbox SET status = 1, delivered_at = #{deliveredAt}, "
                    + "last_error = NULL WHERE id = #{id} AND status = 0")
    int markDelivered(@Param("id") long id, @Param("deliveredAt") LocalDateTime deliveredAt);

    /**
     * Schedules another attempt after a failure.
     *
     * @param id intent id
     * @param retryCount attempts failed so far
     * @param nextRetryAt next attempt time
     * @param lastError failure reason
     * @return affected rows
     * @author Caspian
     * @since 0.6.0
     */
    @Update(
            "UPDATE audit_outbox SET retry_count = #{retryCount}, "
                    + "next_retry_at = #{nextRetryAt}, last_error = #{lastError} "
                    + "WHERE id = #{id} AND status = 0")
    int markRetry(
            @Param("id") long id,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError);

    /**
     * Stops retrying an intent after the attempt limit.
     *
     * @param id intent id
     * @param retryCount attempts failed so far
     * @param lastError final failure reason
     * @return affected rows
     * @author Caspian
     * @since 0.6.0
     */
    @Update(
            "UPDATE audit_outbox SET status = 2, retry_count = #{retryCount}, "
                    + "last_error = #{lastError} WHERE id = #{id} AND status = 0")
    int markGivenUp(
            @Param("id") long id,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError);

    /**
     * Counts rows in one delivery status.
     *
     * @param status 0 pending, 1 delivered, 2 given up
     * @return row count
     * @author Caspian
     * @since 0.6.0
     */
    @Select("SELECT COUNT(*) FROM audit_outbox WHERE status = #{status}")
    long countByStatus(@Param("status") int status);
}
