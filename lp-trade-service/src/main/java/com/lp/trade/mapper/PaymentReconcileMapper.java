package com.lp.trade.mapper;

import com.lp.trade.entity.PaymentReconcile;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Writes {@code payment_reconcile} rows.
 *
 * <p>The upsert relies on {@code uk_channel_date_type}, so importing the same channel, bill day and
 * bill type again updates the aggregate instead of adding a second row. Read queries are introduced
 * together with the reconciliation calculation in a later change.
 *
 * @author Caspian
 * @since 0.6.1
 */
@Mapper
public interface PaymentReconcileMapper {

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
}
