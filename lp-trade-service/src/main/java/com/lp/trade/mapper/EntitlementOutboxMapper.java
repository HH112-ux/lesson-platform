package com.lp.trade.mapper;

import com.lp.trade.entity.EntitlementOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code entitlement_outbox}.
 *
 * <p>The unique key {@code uk_order_course} makes the write inside the order transaction
 * idempotent: a duplicate insert is ignored and the first outstanding intent stays.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface EntitlementOutboxMapper {

    /**
     * Inserts one delivery intent and fills the generated id.
     *
     * @param outbox intent row
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO entitlement_outbox (order_id, order_no, user_id, course_id, status, "
                    + "retry_count, next_retry_at) VALUES (#{orderId}, #{orderNo}, #{userId}, "
                    + "#{courseId}, 0, 0, CURRENT_TIMESTAMP(3))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EntitlementOutbox outbox);

    /**
     * Loads pending intents whose retry time has arrived, oldest first.
     *
     * @param limit maximum rows
     * @return pending intents, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_id AS orderId, order_no AS orderNo, user_id AS userId, "
                    + "course_id AS courseId, status, retry_count AS retryCount, "
                    + "next_retry_at AS nextRetryAt, delivered_at AS deliveredAt, "
                    + "last_error AS lastError FROM entitlement_outbox "
                    + "WHERE status = 0 AND deleted = 0 "
                    + "AND next_retry_at <= CURRENT_TIMESTAMP(3) ORDER BY id LIMIT #{limit}")
    List<EntitlementOutbox> selectPending(@Param("limit") int limit);

    /**
     * Marks one intent as delivered.
     *
     * @param id intent id
     * @param deliveredAt delivery time
     * @return affected rows, {@code 0} when another relay already finished it
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE entitlement_outbox SET status = 1, delivered_at = #{deliveredAt}, "
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
     * @since 0.1.0
     */
    @Update(
            "UPDATE entitlement_outbox SET retry_count = #{retryCount}, "
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
     * @param lastError final failure reason
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE entitlement_outbox SET status = 2, last_error = #{lastError} "
                    + "WHERE id = #{id} AND status = 0")
    int markGivenUp(@Param("id") long id, @Param("lastError") String lastError);
}
