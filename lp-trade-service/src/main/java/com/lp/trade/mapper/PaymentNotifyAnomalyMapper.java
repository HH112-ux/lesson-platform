package com.lp.trade.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Writes callbacks that passed verification but could not be applied.
 *
 * <p>The unique key {@code uk_channel_trade_reason} makes the record idempotent, so a channel retry
 * never produces a second row for the same trade and reason.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Mapper
public interface PaymentNotifyAnomalyMapper {

    /**
     * Inserts one anomaly record.
     *
     * @param channel payment channel
     * @param orderNo order number carried by the callback
     * @param channelTradeNo channel trade number
     * @param expectedAmount local amount due, {@code null} when the order is unknown
     * @param notifiedAmount amount reported by the channel
     * @param tradeStatus raw channel trade status
     * @param reason anomaly reason
     * @param detail extra context for operators
     * @return affected rows
     * @throws org.springframework.dao.DuplicateKeyException when the same trade and reason were
     *     already recorded
     */
    @Insert(
            "INSERT INTO payment_notify_anomaly (channel, order_no, channel_trade_no, "
                    + "expected_amount, notified_amount, trade_status, reason, detail) "
                    + "VALUES (#{channel}, #{orderNo}, #{channelTradeNo}, #{expectedAmount}, "
                    + "#{notifiedAmount}, #{tradeStatus}, #{reason}, #{detail})")
    int insert(
            @Param("channel") int channel,
            @Param("orderNo") String orderNo,
            @Param("channelTradeNo") String channelTradeNo,
            @Param("expectedAmount") BigDecimal expectedAmount,
            @Param("notifiedAmount") BigDecimal notifiedAmount,
            @Param("tradeStatus") String tradeStatus,
            @Param("reason") int reason,
            @Param("detail") String detail);
}
