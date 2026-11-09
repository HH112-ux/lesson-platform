package com.lp.trade.mapper;

import com.lp.trade.entity.PaymentRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code payment_record}.
 *
 * <p>The success update is conditional on the pending status, and {@code uk_channel_trade} is the
 * final guard, so a repeated channel callback can never grant ownership twice.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface PaymentMapper {

    /**
     * Loads the newest payment attempt of an order.
     *
     * @param orderId order id
     * @return newest payment record, or {@code null} when none was created
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_id AS orderId, pay_no AS payNo, channel, "
                    + "pay_scene AS payScene, amount, status, "
                    + "channel_trade_no AS channelTradeNo, paid_at AS paidAt "
                    + "FROM payment_record WHERE order_id = #{orderId} AND deleted = 0 "
                    + "ORDER BY id DESC LIMIT 1")
    PaymentRecord selectLatestByOrderId(@Param("orderId") long orderId);

    /**
     * Finds a successful payment by the channel trade number.
     *
     * <p>This is the idempotency pre-check for a repeated callback.
     *
     * @param channel payment channel
     * @param channelTradeNo channel trade number
     * @return processed payment, or {@code null} when the callback is new
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_id AS orderId, pay_no AS payNo, channel, "
                    + "pay_scene AS payScene, amount, status, "
                    + "channel_trade_no AS channelTradeNo, paid_at AS paidAt "
                    + "FROM payment_record WHERE channel = #{channel} "
                    + "AND channel_trade_no = #{channelTradeNo} AND deleted = 0 LIMIT 1")
    PaymentRecord selectByChannelTradeNo(
            @Param("channel") int channel, @Param("channelTradeNo") String channelTradeNo);

    /**
     * Loads one payment attempt by primary key.
     *
     * @param id payment primary key
     * @return payment record, or {@code null} when it was removed
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT id, order_id AS orderId, pay_no AS payNo, channel, "
                    + "pay_scene AS payScene, amount, status, "
                    + "channel_trade_no AS channelTradeNo, paid_at AS paidAt "
                    + "FROM payment_record WHERE id = #{id} AND deleted = 0")
    PaymentRecord selectById(@Param("id") long id);

    /**
     * Inserts one payment attempt and fills the generated id.
     *
     * @param record payment record
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO payment_record (order_id, pay_no, channel, pay_scene, amount, status) "
                    + "VALUES (#{orderId}, #{payNo}, #{channel}, #{payScene}, #{amount}, "
                    + "#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPayment(PaymentRecord record);

    /**
     * Marks a pending payment as successful.
     *
     * @param id payment primary key
     * @param channelTradeNo channel trade number
     * @param paidAt channel payment time
     * @return affected rows, {@code 0} when another callback already succeeded
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE payment_record SET status = 1, channel_trade_no = #{channelTradeNo}, "
                    + "paid_at = #{paidAt} WHERE id = #{id} AND status = 0")
    int markSuccess(
            @Param("id") long id,
            @Param("channelTradeNo") String channelTradeNo,
            @Param("paidAt") LocalDateTime paidAt);

    /**
     * Marks one successful payment as refunded after the channel confirmed the money movement.
     *
     * @param id payment primary key
     * @return affected rows, {@code 0} when the payment is not successful
     * @author Caspian
     * @since 0.2.0
     */
    @Update("UPDATE payment_record SET status = 3 WHERE id = #{id} AND status = 1")
    int markRefunded(@Param("id") long id);

    /**
     * Records a payment that arrived after the attempt was closed.
     *
     * <p>The row is moved to the dedicated late-paid state instead of success so no ownership is
     * granted, while the channel trade number and payment time stay available for reconciliation
     * and manual refund. Only closed attempts are touched; a concurrent success keeps its state.
     *
     * @param id payment primary key
     * @param channelTradeNo channel trade number
     * @param paidAt channel payment time
     * @return affected rows, {@code 0} when the attempt was not closed anymore
     * @author Caspian
     * @since 0.2.0
     */
    @Update(
            "UPDATE payment_record SET status = 6, channel_trade_no = #{channelTradeNo}, "
                    + "paid_at = #{paidAt} WHERE id = #{id} AND status = 2")
    int markPaidAfterClose(
            @Param("id") long id,
            @Param("channelTradeNo") String channelTradeNo,
            @Param("paidAt") LocalDateTime paidAt);

    /**
     * Closes all pending attempts of an order.
     *
     * @param orderId order id
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Update("UPDATE payment_record SET status = 2 WHERE order_id = #{orderId} AND status = 0")
    int closePendingByOrderId(@Param("orderId") long orderId);
}
