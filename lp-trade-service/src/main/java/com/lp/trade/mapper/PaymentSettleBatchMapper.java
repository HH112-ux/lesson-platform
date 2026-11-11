package com.lp.trade.mapper;

import com.lp.trade.entity.PaymentSettleBatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code payment_settle_batch}.
 *
 * <p>The upsert relies on {@code uk_active_day}, so closing the same channel and bill day twice
 * keeps one active row and returns the same primary key; two concurrent closes also converge to
 * that one row. A voided row has a {@code null} generated key, so closing that day again inserts a
 * new batch and keeps the voided row as history. No statement here touches orders, payments,
 * refunds or funds.
 *
 * @author Caspian
 * @since 0.6.2
 */
@Mapper
public interface PaymentSettleBatchMapper {

    /**
     * Loads the active batch of one channel and bill day.
     *
     * <p>Voided rows are excluded, so a day with only voided history looks unclosed and can be
     * closed again as a new batch.
     *
     * @param channel payment channel
     * @param billDate bill day in Asia/Shanghai
     * @return active batch or {@code null}
     * @author Caspian
     * @since 0.6.2
     */
    @Select(
            "SELECT id, channel, bill_date AS billDate, "
                    + "channel_pay_count AS channelPayCount, "
                    + "channel_pay_amount AS channelPayAmount, "
                    + "channel_refund_count AS channelRefundCount, "
                    + "channel_refund_amount AS channelRefundAmount, "
                    + "local_pay_count AS localPayCount, "
                    + "local_pay_amount AS localPayAmount, "
                    + "local_refund_count AS localRefundCount, "
                    + "local_refund_amount AS localRefundAmount, "
                    + "net_channel_amount AS netChannelAmount, "
                    + "net_local_amount AS netLocalAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, closed_by AS closedBy, "
                    + "closed_at AS closedAt, void_by AS voidBy, void_at AS voidAt "
                    + "FROM payment_settle_batch "
                    + "WHERE channel = #{channel} AND bill_date = #{billDate} AND status = 1")
    PaymentSettleBatch selectActiveByKey(
            @Param("channel") Integer channel, @Param("billDate") LocalDate billDate);

    /**
     * Loads one batch by primary key.
     *
     * @param id primary key
     * @return batch or {@code null}
     * @author Caspian
     * @since 0.6.2
     */
    @Select(
            "SELECT id, channel, bill_date AS billDate, "
                    + "channel_pay_count AS channelPayCount, "
                    + "channel_pay_amount AS channelPayAmount, "
                    + "channel_refund_count AS channelRefundCount, "
                    + "channel_refund_amount AS channelRefundAmount, "
                    + "local_pay_count AS localPayCount, "
                    + "local_pay_amount AS localPayAmount, "
                    + "local_refund_count AS localRefundCount, "
                    + "local_refund_amount AS localRefundAmount, "
                    + "net_channel_amount AS netChannelAmount, "
                    + "net_local_amount AS netLocalAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, closed_by AS closedBy, "
                    + "closed_at AS closedAt, void_by AS voidBy, void_at AS voidAt "
                    + "FROM payment_settle_batch "
                    + "WHERE id = #{id}")
    PaymentSettleBatch selectById(@Param("id") Long id);

    /**
     * Inserts one active batch of the day.
     *
     * <p>{@code uk_active_day} only covers status 1 rows, so a voided row never blocks the insert
     * and a re-close after a void creates a new batch instead of reviving history. Two concurrent
     * inserts of the same active day collide on the unique key; the caller turns that collision
     * into a business error rather than an overwrite.
     *
     * @param batch batch carrying the computed figures and the closing operator
     * @return affected rows
     * @author Caspian
     * @since 0.6.2
     */
    @Insert(
            "INSERT INTO payment_settle_batch (channel, bill_date, active_day_key, "
                    + "channel_pay_count, channel_pay_amount, channel_refund_count, "
                    + "channel_refund_amount, local_pay_count, local_pay_amount, "
                    + "local_refund_count, local_refund_amount, net_channel_amount, "
                    + "net_local_amount, diff_count, diff_amount, status, closed_by, closed_at) "
                    + "VALUES (#{channel}, #{billDate}, "
                    + "CONCAT(#{channel}, '-', #{billDate}), #{channelPayCount}, "
                    + "#{channelPayAmount}, #{channelRefundCount}, #{channelRefundAmount}, "
                    + "#{localPayCount}, #{localPayAmount}, #{localRefundCount}, "
                    + "#{localRefundAmount}, #{netChannelAmount}, #{netLocalAmount}, "
                    + "#{diffCount}, #{diffAmount}, 1, #{closedBy}, #{closedAt})")
    int insert(PaymentSettleBatch batch);

    /**
     * Voids a closed batch so its bill day can be imported and closed again.
     *
     * <p>Only status 1 can transition to 2; a zero return means the batch was missing or already
     * voided.
     *
     * @param id primary key
     * @param voidBy operator user id
     * @param voidAt void time
     * @return affected rows
     * @author Caspian
     * @since 0.6.2
     */
    @Update(
            "UPDATE payment_settle_batch SET status = 2, active_day_key = NULL, "
                    + "void_by = #{voidBy}, void_at = #{voidAt} "
                    + "WHERE id = #{id} AND status = 1")
    int voidBatch(
            @Param("id") Long id,
            @Param("voidBy") Long voidBy,
            @Param("voidAt") LocalDateTime voidAt);

    /**
     * Counts the closed batches of one channel and bill day, used to lock a settled bill day.
     *
     * @param channel payment channel
     * @param billDate bill day in Asia/Shanghai
     * @return number of status 1 rows, zero when the day is not closed
     * @author Caspian
     * @since 0.6.2
     */
    @Select(
            "SELECT COUNT(*) FROM payment_settle_batch WHERE channel = #{channel} "
                    + "AND bill_date = #{billDate} AND status = 1")
    long countClosed(@Param("channel") Integer channel, @Param("billDate") LocalDate billDate);

    /**
     * Lists batches for the settlement query.
     *
     * @param channel optional channel filter
     * @param billDate optional bill day filter
     * @param status optional status filter
     * @param offset zero-based offset
     * @param pageSize page size
     * @return page of batches ordered by bill day desc then channel
     * @author Caspian
     * @since 0.6.2
     */
    @Select(
            "<script>SELECT id, channel, bill_date AS billDate, "
                    + "channel_pay_count AS channelPayCount, "
                    + "channel_pay_amount AS channelPayAmount, "
                    + "channel_refund_count AS channelRefundCount, "
                    + "channel_refund_amount AS channelRefundAmount, "
                    + "local_pay_count AS localPayCount, "
                    + "local_pay_amount AS localPayAmount, "
                    + "local_refund_count AS localRefundCount, "
                    + "local_refund_amount AS localRefundAmount, "
                    + "net_channel_amount AS netChannelAmount, "
                    + "net_local_amount AS netLocalAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, closed_by AS closedBy, "
                    + "closed_at AS closedAt, void_by AS voidBy, void_at AS voidAt "
                    + "FROM payment_settle_batch WHERE 1 = 1 "
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='billDate != null'>AND bill_date = #{billDate} </if>"
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "ORDER BY bill_date DESC, channel ASC "
                    + "LIMIT #{offset}, #{pageSize}</script>")
    List<PaymentSettleBatch> page(
            @Param("channel") Integer channel,
            @Param("billDate") LocalDate billDate,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    /**
     * Counts batches matching the settlement query filters.
     *
     * @param channel optional channel filter
     * @param billDate optional bill day filter
     * @param status optional status filter
     * @return matching row count
     * @author Caspian
     * @since 0.6.2
     */
    @Select(
            "<script>SELECT COUNT(*) FROM payment_settle_batch WHERE 1 = 1 "
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='billDate != null'>AND bill_date = #{billDate} </if>"
                    + "<if test='status != null'>AND status = #{status} </if></script>")
    long count(
            @Param("channel") Integer channel,
            @Param("billDate") LocalDate billDate,
            @Param("status") Integer status);
}
