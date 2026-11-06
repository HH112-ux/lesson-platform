package com.lp.trade.mapper;

import com.lp.trade.entity.RefundRequest;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code refund_request}.
 *
 * <p>{@code uk_order} is the final guard against a double refund, and {@code uk_refund_no} rejects
 * a duplicated channel request. The locking read at the start of a refund serializes concurrent
 * calls for the same order, so only one of them may call the channel.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface RefundRequestMapper {

    /**
     * Locks the refund row of one order, including the gap when the row does not exist yet.
     *
     * @param orderId order id
     * @return existing refund, or {@code null} before the first request
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, refund_no AS refundNo, order_id AS orderId, payment_id AS paymentId, "
                    + "user_id AS userId, channel, amount, reason, status, "
                    + "channel_refund_no AS channelRefundNo, refunded_at AS refundedAt "
                    + "FROM refund_request WHERE order_id = #{orderId} AND deleted = 0 "
                    + "FOR UPDATE")
    RefundRequest selectByOrderIdForUpdate(@Param("orderId") long orderId);

    /**
     * Inserts one refund request and fills the generated id.
     *
     * @param refund refund row
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO refund_request (refund_no, order_id, payment_id, user_id, channel, amount, "
                    + "reason, status) VALUES (#{refundNo}, #{orderId}, #{paymentId}, #{userId}, "
                    + "#{channel}, #{amount}, #{reason}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RefundRequest refund);

    /**
     * Marks one refund as successful and stores the channel reference.
     *
     * @param id refund primary key
     * @param channelRefundNo channel refund number
     * @param refundedAt refund time
     * @return affected rows, {@code 0} when the refund is no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE refund_request SET status = 1, channel_refund_no = #{channelRefundNo}, "
                    + "refunded_at = #{refundedAt} WHERE id = #{id} AND status = 0")
    int markSuccess(
            @Param("id") long id,
            @Param("channelRefundNo") String channelRefundNo,
            @Param("refundedAt") LocalDateTime refundedAt);
}
