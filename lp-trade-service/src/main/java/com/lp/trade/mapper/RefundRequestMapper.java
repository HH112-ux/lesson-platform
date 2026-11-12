package com.lp.trade.mapper;

import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.entity.AdminRefundRow;
import com.lp.trade.entity.RefundRequest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code refund_request}.
 *
 * <p>The generated column {@code active_order_id} and its unique key guard one in-progress refund
 * per order, while rejected and failed rows stay retryable. Status 5 counts as in progress because
 * the channel outcome is unknown, so the order must not accept another application. Every state
 * transition uses a conditional update, so a duplicated review or channel callback returns the
 * stored decision instead of moving money twice.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface RefundRequestMapper {

    String COLUMNS =
            "id, refund_no AS refundNo, order_id AS orderId, payment_id AS paymentId, "
                    + "user_id AS userId, channel, amount, reason, review_reason AS reviewReason, "
                    + "reviewed_by AS reviewedBy, reviewed_at AS reviewedAt, status, "
                    + "channel_refund_no AS channelRefundNo, "
                    + "reconcile_attempts AS reconcileAttempts, "
                    + "reconcile_checked_at AS reconcileCheckedAt, "
                    + "reconcile_finding AS reconcileFinding, fail_reason AS failReason, "
                    + "refunded_at AS refundedAt";

    /**
     * Loads one refund by primary key.
     *
     * @param id refund primary key
     * @return refund row, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT " + COLUMNS + " FROM refund_request WHERE id = #{id} AND deleted = 0")
    RefundRequest selectById(@Param("id") long id);

    /**
     * Loads the in-progress refund of one order.
     *
     * <p>Statuses 0, 1, 2 and 5 block a new application; statuses 3 and 4 do not.
     *
     * @param orderId order id
     * @return in-progress refund, or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM refund_request WHERE order_id = #{orderId} "
                    + "AND status IN (0, 1, 2, 5) AND deleted = 0 ORDER BY id DESC LIMIT 1")
    RefundRequest selectActiveByOrderId(@Param("orderId") long orderId);

    /**
     * Loads the newest refund of each requested order for the student order list.
     *
     * @param orderIds order ids owned by the caller
     * @return one row per order that has ever applied
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT r.id, r.refund_no AS refundNo, r.order_id AS orderId, "
                    + "r.payment_id AS paymentId, r.user_id AS userId, r.channel, r.amount, "
                    + "r.reason, r.review_reason AS reviewReason, r.reviewed_by AS reviewedBy, "
                    + "r.reviewed_at AS reviewedAt, r.status, "
                    + "r.channel_refund_no AS channelRefundNo, r.fail_reason AS failReason, "
                    + "r.refunded_at AS refundedAt FROM refund_request r "
                    + "INNER JOIN (SELECT order_id, MAX(id) AS max_id FROM refund_request "
                    + "WHERE deleted = 0 AND order_id IN "
                    + "<foreach item='orderId' collection='orderIds' open='(' separator=',' "
                    + "close=')'>#{orderId}</foreach> GROUP BY order_id) latest "
                    + "ON r.id = latest.max_id</script>")
    List<RefundRequest> selectLatestByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /**
     * Counts all historical applications of one order, including rejected and failed rows.
     *
     * <p>The count gives each retry a distinct channel idempotency key.
     *
     * @param orderId order id
     * @return application count
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT COUNT(*) FROM refund_request WHERE order_id = #{orderId} AND deleted = 0")
    int countByOrderId(@Param("orderId") long orderId);

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
     * Moves one pending request to approved.
     *
     * @param id refund primary key
     * @param reviewedBy reviewer user id
     * @param reviewedAt review time
     * @return affected rows, {@code 0} when another reviewer already decided
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE refund_request SET status = 1, reviewed_by = #{reviewedBy}, "
                    + "reviewed_at = #{reviewedAt} WHERE id = #{id} AND status = 0")
    int markApproved(
            @Param("id") long id,
            @Param("reviewedBy") long reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt);

    /**
     * Moves one pending request to rejected and stores the required reason.
     *
     * @param id refund primary key
     * @param reviewReason rejection reason
     * @param reviewedBy reviewer user id
     * @param reviewedAt review time
     * @return affected rows, {@code 0} when another reviewer already decided
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE refund_request SET status = 3, review_reason = #{reviewReason}, "
                    + "reviewed_by = #{reviewedBy}, reviewed_at = #{reviewedAt} "
                    + "WHERE id = #{id} AND status = 0")
    int markRejected(
            @Param("id") long id,
            @Param("reviewReason") String reviewReason,
            @Param("reviewedBy") long reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt);

    /**
     * Marks one approved refund as successful and stores the channel reference.
     *
     * @param id refund primary key
     * @param channelRefundNo channel refund number
     * @param refundedAt refund time
     * @return affected rows, {@code 0} when the refund is not approved
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE refund_request SET status = 2, channel_refund_no = #{channelRefundNo}, "
                    + "refunded_at = #{refundedAt} WHERE id = #{id} AND status = 1")
    int markSuccess(
            @Param("id") long id,
            @Param("channelRefundNo") String channelRefundNo,
            @Param("refundedAt") LocalDateTime refundedAt);

    /**
     * Marks one approved refund as failed after an explicit channel rejection.
     *
     * @param id refund primary key
     * @param failReason failure reason
     * @return affected rows, {@code 0} when the refund is not approved
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE refund_request SET status = 4, fail_reason = #{failReason} "
                    + "WHERE id = #{id} AND status = 1")
    int markFailed(@Param("id") long id, @Param("failReason") String failReason);

    /**
     * Marks one approved refund whose channel outcome is unknown.
     *
     * <p>The status becomes 5 instead of 4, so the order stays refunding and the buyer cannot apply
     * again before operations reconcile the channel bill.
     *
     * @param id refund primary key
     * @param failReason reason including the exception type
     * @return affected rows, {@code 0} when the refund is not approved
     * @author Caspian
     * @since 0.2.0
     */
    @Update(
            "UPDATE refund_request SET status = 5, fail_reason = #{failReason} "
                    + "WHERE id = #{id} AND status = 1")
    int markUnknown(@Param("id") long id, @Param("failReason") String failReason);

    /**
     * Reads refunds whose channel outcome has not been settled locally.
     *
     * <p>Only approved and unknown rows unchanged for the grace period are returned. A row is
     * eligible again after one hour following its first query and after six hours from the second
     * query on, so a refund that never settles cannot be polled every tick. Rows queried least
     * recently come first, which keeps the batch cap from starving old refunds.
     *
     * @param graceBefore rows unchanged since this instant are eligible for their first query
     * @param firstRetryBefore rows last queried before this instant are eligible for a second query
     * @param laterRetryBefore rows last queried before this instant are eligible for later queries
     * @param limit maximum rows per scan
     * @return refunds awaiting channel confirmation
     * @author Caspian
     * @since 0.7.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM refund_request WHERE status IN (1, 5) AND deleted = 0 "
                    + "AND updated_at <= #{graceBefore} "
                    + "AND (reconcile_checked_at IS NULL "
                    + "OR (reconcile_attempts = 1 AND reconcile_checked_at <= #{firstRetryBefore}) "
                    + "OR (reconcile_attempts >= 2 AND reconcile_checked_at <= #{laterRetryBefore})) "
                    + "ORDER BY COALESCE(reconcile_checked_at, created_at) ASC, id ASC "
                    + "LIMIT #{limit}")
    List<RefundRequest> selectReconcileCandidates(
            @Param("graceBefore") LocalDateTime graceBefore,
            @Param("firstRetryBefore") LocalDateTime firstRetryBefore,
            @Param("laterRetryBefore") LocalDateTime laterRetryBefore,
            @Param("limit") int limit);

    /**
     * Records one read-only reconciliation query on a refund that is still unsettled.
     *
     * <p>The status predicate guarantees the bookkeeping can never revive a settled refund.
     *
     * @param id refund primary key
     * @param finding finding code of this query
     * @param checkedAt query time
     * @return affected rows, {@code 0} when the refund already settled
     * @author Caspian
     * @since 0.7.0
     */
    @Update(
            "UPDATE refund_request SET reconcile_attempts = reconcile_attempts + 1, "
                    + "reconcile_checked_at = #{checkedAt}, reconcile_finding = #{finding} "
                    + "WHERE id = #{id} AND status IN (1, 5) AND deleted = 0")
    int markReconcileChecked(
            @Param("id") long id,
            @Param("finding") int finding,
            @Param("checkedAt") LocalDateTime checkedAt);

    /**
     * Counts refunds still waiting for a settled channel outcome.
     *
     * @return approved and unknown refunds
     * @author Caspian
     * @since 0.7.0
     */
    @Select("SELECT COUNT(*) FROM refund_request WHERE status IN (1, 5) AND deleted = 0")
    long countReconcilePending();

    /**
     * Counts refunds the channel reports as refunded while the local status is still unsettled.
     *
     * @return refunds needing a human follow-up until automatic convergence exists
     * @author Caspian
     * @since 0.7.0
     */
    @Select(
            "SELECT COUNT(*) FROM refund_request WHERE status IN (1, 5) AND deleted = 0 "
                    + "AND reconcile_finding = "
                    + RefundReconcileFinding.CHANNEL_DONE)
    long countReconcileChannelDone();

    /**
     * Returns when the oldest unsettled refund was created.
     *
     * @return creation time of the oldest unsettled refund, or {@code null} when none exists
     * @author Caspian
     * @since 0.7.0
     */
    @Select("SELECT MIN(created_at) FROM refund_request WHERE status IN (1, 5) AND deleted = 0")
    LocalDateTime oldestReconcileCreatedAt();

    /**
     * Counts refunds for the operations review list.
     *
     * @param status status filter, {@code null} for all statuses
     * @return matching rows
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM refund_request r WHERE r.deleted = 0 "
                    + "<if test='status != null'>AND r.status = #{status}</if></script>")
    long countForAdmin(@Param("status") Integer status);

    /**
     * Reads one page of refunds for the operations review list.
     *
     * @param status status filter, {@code null} for all statuses
     * @param offset zero based row offset
     * @param limit page size
     * @return joined refund and order rows, newest first
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT r.id, r.refund_no AS refundNo, r.order_id AS orderId, "
                    + "o.order_no AS orderNo, r.user_id AS userId, r.channel, r.amount, r.reason, "
                    + "r.review_reason AS reviewReason, r.status, r.fail_reason AS failReason, "
                    + "r.reviewed_by AS reviewedBy, r.reviewed_at AS reviewedAt, "
                    + "r.refunded_at AS refundedAt, r.created_at AS createdAt "
                    + "FROM refund_request r LEFT JOIN trade_order o ON o.id = r.order_id "
                    + "WHERE r.deleted = 0 "
                    + "<if test='status != null'>AND r.status = #{status}</if> "
                    + "ORDER BY r.id DESC LIMIT #{offset}, #{limit}</script>")
    List<AdminRefundRow> selectAdminPage(
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
