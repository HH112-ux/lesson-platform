package com.lp.trade.mapper;

import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code payment_reconcile}, and reads the local trade rows it compares against.
 *
 * <p>The upsert relies on {@code uk_channel_date_type}, so importing the same channel, bill day and
 * bill type again updates the aggregate instead of adding a second row. The raw CSV text and the
 * difference JSON are stored on the row, which keeps rechecks reproducible without an object store.
 *
 * @author Caspian
 * @since 0.6.1
 */
@Mapper
public interface PaymentReconcileMapper {

    /**
     * Loads one aggregate by its unique key.
     *
     * @param channel payment channel
     * @param billDate bill day in Asia/Shanghai
     * @param billType 1 payment bill, 2 refund bill
     * @return aggregate or {@code null}
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "SELECT id, channel, bill_date AS billDate, bill_type AS billType, "
                    + "channel_count AS channelCount, "
                    + "channel_amount AS channelAmount, local_count AS localCount, "
                    + "local_amount AS localAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, handled_by AS handledBy, "
                    + "handled_at AS handledAt, raw_bill AS rawBill, "
                    + "diff_detail AS diffDetail FROM payment_reconcile "
                    + "WHERE channel = #{channel} AND bill_date = #{billDate} "
                    + "AND bill_type = #{billType} AND deleted = 0")
    PaymentReconcile selectByKey(
            @Param("channel") Integer channel,
            @Param("billDate") LocalDate billDate,
            @Param("billType") Integer billType);

    /**
     * Loads one aggregate by primary key.
     *
     * @param id primary key
     * @return aggregate or {@code null}
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "SELECT id, channel, bill_date AS billDate, bill_type AS billType, "
                    + "channel_count AS channelCount, "
                    + "channel_amount AS channelAmount, local_count AS localCount, "
                    + "local_amount AS localAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, handled_by AS handledBy, "
                    + "handled_at AS handledAt, raw_bill AS rawBill, "
                    + "diff_detail AS diffDetail FROM payment_reconcile "
                    + "WHERE id = #{id} AND deleted = 0")
    PaymentReconcile selectById(@Param("id") Long id);

    /**
     * Inserts or updates one aggregate by its unique key.
     *
     * @param reconcile aggregate carrying all computed fields
     * @return affected rows
     * @author Caspian
     * @since 0.6.1
     */
    @Insert(
            "INSERT INTO payment_reconcile (channel, bill_date, bill_type, channel_count, "
                    + "channel_amount, local_count, local_amount, diff_count, diff_amount, "
                    + "raw_bill, diff_detail, status, handled_by, handled_at, deleted) VALUES "
                    + "(#{channel}, #{billDate}, #{billType}, #{channelCount}, #{channelAmount}, "
                    + "#{localCount}, #{localAmount}, #{diffCount}, #{diffAmount}, #{rawBill}, "
                    + "#{diffDetail}, #{status}, #{handledBy}, #{handledAt}, 0) "
                    + "ON DUPLICATE KEY UPDATE channel_count = VALUES(channel_count), "
                    + "channel_amount = VALUES(channel_amount), local_count = VALUES(local_count), "
                    + "local_amount = VALUES(local_amount), diff_count = VALUES(diff_count), "
                    + "diff_amount = VALUES(diff_amount), raw_bill = VALUES(raw_bill), "
                    + "diff_detail = VALUES(diff_detail), status = VALUES(status), "
                    + "handled_by = VALUES(handled_by), handled_at = VALUES(handled_at), deleted = 0")
    int upsert(PaymentReconcile reconcile);

    /**
     * Lists aggregates for the daily query.
     *
     * @param channel optional channel filter
     * @param billDate optional bill day filter
     * @param status optional status filter
     * @param offset zero-based offset
     * @param pageSize page size
     * @return page of aggregates ordered by bill day desc then channel
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "<script>SELECT id, channel, bill_date AS billDate, bill_type AS billType, "
                    + "channel_count AS channelCount, "
                    + "channel_amount AS channelAmount, local_count AS localCount, "
                    + "local_amount AS localAmount, diff_count AS diffCount, "
                    + "diff_amount AS diffAmount, status, handled_by AS handledBy, "
                    + "handled_at AS handledAt FROM payment_reconcile WHERE deleted = 0 "
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='billDate != null'>AND bill_date = #{billDate} </if>"
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "ORDER BY bill_date DESC, channel ASC, bill_type ASC "
                    + "LIMIT #{offset}, #{pageSize}</script>")
    List<PaymentReconcile> page(
            @Param("channel") Integer channel,
            @Param("billDate") LocalDate billDate,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    /**
     * Counts aggregates matching the daily query filters.
     *
     * @param channel optional channel filter
     * @param billDate optional bill day filter
     * @param status optional status filter
     * @return matching row count
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "<script>SELECT COUNT(*) FROM payment_reconcile WHERE deleted = 0 "
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='billDate != null'>AND bill_date = #{billDate} </if>"
                    + "<if test='status != null'>AND status = #{status} </if></script>")
    long count(
            @Param("channel") Integer channel,
            @Param("billDate") LocalDate billDate,
            @Param("status") Integer status);

    /**
     * Counts unresolved aggregates for one channel and bill day.
     *
     * @param channel payment channel
     * @param billDate bill day in Asia/Shanghai
     * @return number of status 2 rows
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "SELECT COUNT(*) FROM payment_reconcile WHERE channel = #{channel} "
                    + "AND bill_date = #{billDate} AND status = 2 AND deleted = 0")
    long countUnresolved(@Param("channel") Integer channel, @Param("billDate") LocalDate billDate);

    /**
     * Marks a pending aggregate as handled. Only status 2 can transition to 3.
     *
     * @param id primary key
     * @param handledBy operator user id
     * @param handledAt handled time
     * @return affected rows, zero when the row is missing or not pending
     * @author Caspian
     * @since 0.6.1
     */
    @Update(
            "UPDATE payment_reconcile SET status = 3, handled_by = #{handledBy}, "
                    + "handled_at = #{handledAt} WHERE id = #{id} AND status = 2 AND deleted = 0")
    int markHandled(
            @Param("id") Long id,
            @Param("handledBy") Long handledBy,
            @Param("handledAt") LocalDateTime handledAt);

    /**
     * Loads successful local payments that belong to the bill day.
     *
     * @param channel payment channel
     * @param start inclusive start in Asia/Shanghai
     * @param end exclusive end in Asia/Shanghai
     * @return local payment rows with id, pay number, channel trade number and amount
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "SELECT id, pay_no AS payNo, channel_trade_no AS channelTradeNo, amount "
                    + "FROM payment_record "
                    + "WHERE channel = #{channel} AND status IN (1, 3, 6) "
                    + "AND paid_at >= #{start} AND paid_at < #{end} AND deleted = 0")
    List<PaymentRecord> selectLocalPayments(
            @Param("channel") Integer channel,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Loads successful local refunds that belong to the bill day.
     *
     * @param channel payment channel
     * @param start inclusive start in Asia/Shanghai
     * @param end exclusive end in Asia/Shanghai
     * @return local refund rows with id, refund number, channel refund number and amount
     * @author Caspian
     * @since 0.6.1
     */
    @Select(
            "SELECT id, refund_no AS refundNo, channel_refund_no AS channelRefundNo, amount "
                    + "FROM refund_request "
                    + "WHERE channel = #{channel} AND status = 2 "
                    + "AND refunded_at >= #{start} AND refunded_at < #{end} AND deleted = 0")
    List<RefundRequest> selectLocalRefunds(
            @Param("channel") Integer channel,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
