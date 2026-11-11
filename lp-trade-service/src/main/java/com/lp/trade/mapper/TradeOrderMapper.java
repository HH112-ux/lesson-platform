package com.lp.trade.mapper;

import com.lp.trade.entity.AdminOrderRow;
import com.lp.trade.entity.CourseSaleRow;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes orders in {@code trade_order} and {@code trade_order_item}.
 *
 * <p>The unique keys {@code uk_idempotency} and {@code uk_order_no} are the final guards for
 * concurrent requests; the service pre-checks them only to return a friendly replay response.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface TradeOrderMapper {

    /**
     * Counts paid or refunding orders that already contain the course.
     *
     * <p>Status 1 (paid) and 4 (refunding) both mean the student still owns access, so adding the
     * course to the cart must be rejected.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @return matching order count, {@code 0} when never bought
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT COUNT(*) FROM trade_order_item i "
                    + "INNER JOIN trade_order o ON o.id = i.order_id "
                    + "WHERE o.user_id = #{userId} AND i.course_id = #{courseId} "
                    + "AND o.deleted = 0 AND i.deleted = 0 AND o.status IN (1, 4)")
    long countOwned(@Param("userId") long userId, @Param("courseId") long courseId);

    /**
     * Counts orders that still grant the course, so a sold course locks lesson deletion.
     *
     * <p>Status 1 (paid) and 4 (refunding) both keep the buyer's entitlement; only 5 (refunded)
     * releases it.
     *
     * @param courseId course id
     * @return granting order count, {@code 0} when never sold
     * @author Caspian
     * @since 0.15.0
     */
    @Select(
            "SELECT COUNT(*) FROM trade_order_item i "
                    + "INNER JOIN trade_order o ON o.id = i.order_id "
                    + "WHERE i.course_id = #{courseId} "
                    + "AND o.deleted = 0 AND i.deleted = 0 AND o.status IN (1, 4)")
    long countPaidByCourse(@Param("courseId") long courseId);

    /**
     * Finds an order by the client idempotency key.
     *
     * @param userId buyer user id
     * @param idempotencyKey client supplied key
     * @return existing order, or {@code null} on the first call
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey FROM trade_order "
                    + "WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey} "
                    + "AND deleted = 0 LIMIT 1")
    TradeOrder selectByUserAndIdempotencyKey(
            @Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    /**
     * Finds an order by its business number.
     *
     * @param orderNo business order number
     * @return order, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey FROM trade_order "
                    + "WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    TradeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Locks an order by its business number for the rest of the current transaction.
     *
     * <p>Payment initiation serialises on this row so two concurrent clicks cannot both observe "no
     * pending attempt" and insert one each.
     *
     * @param orderNo business order number
     * @return order, or {@code null} when absent
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey FROM trade_order "
                    + "WHERE order_no = #{orderNo} AND deleted = 0 FOR UPDATE")
    TradeOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    /**
     * Finds an order by id regardless of its owner.
     *
     * @param orderId order primary key
     * @return order, or {@code null} when absent or deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey, created_at AS createdAt, "
                    + "cancel_reason AS cancelReason FROM trade_order "
                    + "WHERE id = #{orderId} AND deleted = 0 LIMIT 1")
    TradeOrder selectById(@Param("orderId") long orderId);

    /**
     * Locks one order row for the duration of the transaction.
     *
     * <p>Refund applications use this read to serialize concurrent submissions for the same order;
     * the generated unique key on the refund table remains the final guard.
     *
     * @param orderId order primary key
     * @return locked order, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey, created_at AS createdAt, "
                    + "cancel_reason AS cancelReason FROM trade_order "
                    + "WHERE id = #{orderId} AND deleted = 0 FOR UPDATE")
    TradeOrder selectByIdForUpdate(@Param("orderId") long orderId);

    /**
     * Counts the orders of one buyer, optionally filtered by status.
     *
     * @param userId buyer user id
     * @param status status filter, {@code null} for all statuses
     * @return matching orders
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM trade_order WHERE user_id = #{userId} "
                    + "AND deleted = 0 "
                    + "<if test='status != null'>AND status = #{status} </if></script>")
    long countByUser(@Param("userId") long userId, @Param("status") Integer status);

    /**
     * Lists one page of buyer orders, newest first.
     *
     * @param userId buyer user id
     * @param status status filter, {@code null} for all statuses
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return matching orders
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey, created_at AS createdAt, "
                    + "cancel_reason AS cancelReason FROM trade_order "
                    + "WHERE user_id = #{userId} AND deleted = 0 "
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<TradeOrder> selectPageByUser(
            @Param("userId") long userId,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * Cancels a pending order; the conditional status keeps a paid order untouched.
     *
     * @param id order primary key
     * @param reason buyer supplied reason, may be {@code null}
     * @param cancelledAt cancel time
     * @return affected rows, {@code 0} when the order is no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE trade_order SET status = 2, cancel_reason = #{reason}, "
                    + "closed_at = #{cancelledAt} WHERE id = #{id} AND status = 0")
    int markCancelled(
            @Param("id") long id,
            @Param("reason") String reason,
            @Param("cancelledAt") LocalDateTime cancelledAt);

    /**
     * Marks a pending order as paid.
     *
     * @param id order primary key
     * @param paidAt payment time
     * @return affected rows, {@code 0} when the order is no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE trade_order SET status = 1, paid_at = #{paidAt} "
                    + "WHERE id = #{id} AND status = 0")
    int markPaid(@Param("id") long id, @Param("paidAt") LocalDateTime paidAt);

    /**
     * Marks an approved refund as in progress so no other refund can leave the paid state.
     *
     * @param id order primary key
     * @return affected rows, {@code 0} when the order is no longer paid
     * @author Caspian
     * @since 0.1.0
     */
    @Update("UPDATE trade_order SET status = 4 WHERE id = #{id} AND status = 1")
    int markRefunding(@Param("id") long id);

    /**
     * Marks one paid order as refunded after the channel confirmed the money movement.
     *
     * @param id order primary key
     * @param refundedAt refund time
     * @return affected rows, {@code 0} when the order is not refunding
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE trade_order SET status = 5, closed_at = #{refundedAt} "
                    + "WHERE id = #{id} AND status = 4")
    int markRefunded(@Param("id") long id, @Param("refundedAt") LocalDateTime refundedAt);

    /**
     * Returns a refunding order to paid after the channel refund failed.
     *
     * @param id order primary key
     * @return affected rows, {@code 0} when the order is not refunding
     * @author Caspian
     * @since 0.1.0
     */
    @Update("UPDATE trade_order SET status = 1 WHERE id = #{id} AND status = 4")
    int markPaidAfterRefundFailure(@Param("id") long id);

    /**
     * Marks a pending order as closed.
     *
     * @param id order primary key
     * @param closedAt close time
     * @return affected rows, {@code 0} when the order is no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE trade_order SET status = 3, closed_at = #{closedAt} "
                    + "WHERE id = #{id} AND status = 0")
    int markClosed(@Param("id") long id, @Param("closedAt") LocalDateTime closedAt);

    /**
     * Lists pending orders whose payment deadline has passed.
     *
     * <p>Uses {@code idx_status_expire} so the periodic timeout scan does not read the whole table.
     *
     * @param now scan time
     * @param limit maximum rows per scan
     * @return expired pending orders ordered by id
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT id, order_no AS orderNo, user_id AS userId, "
                    + "total_amount AS totalAmount, discount_amount AS discountAmount, "
                    + "pay_amount AS payAmount, status, coupon_id AS couponId, channel, "
                    + "pay_expire_at AS payExpireAt, paid_at AS paidAt, closed_at AS closedAt, "
                    + "idempotency_key AS idempotencyKey FROM trade_order "
                    + "WHERE status = 0 AND deleted = 0 AND pay_expire_at IS NOT NULL "
                    + "AND pay_expire_at < #{now} ORDER BY id LIMIT #{limit}")
    List<TradeOrder> selectExpiredPending(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * Closes a pending order because its payment deadline passed.
     *
     * <p>The {@code status = 0} predicate makes the close lose against a payment callback that
     * already committed, so a paid order can never be closed by the scan.
     *
     * @param id order primary key
     * @param closedAt close time
     * @return affected rows, {@code 0} when the order was paid or already closed
     * @author Caspian
     * @since 0.2.0
     */
    @Update(
            "UPDATE trade_order SET status = 3, cancel_reason = 'payment timeout', "
                    + "closed_at = #{closedAt} WHERE id = #{id} AND status = 0")
    int markTimedOut(@Param("id") long id, @Param("closedAt") LocalDateTime closedAt);

    /**
     * Loads the first line of an order.
     *
     * <p>The first release sells one course per order, so the first line is the only line.
     *
     * @param orderId order id
     * @return line, or {@code null} when the order has none
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_id AS orderId, course_id AS courseId, "
                    + "owner_teacher_id AS ownerTeacherId, course_title AS courseTitle, price, "
                    + "discount_amount AS discountAmount, actual_amount AS actualAmount "
                    + "FROM trade_order_item WHERE order_id = #{orderId} AND deleted = 0 "
                    + "ORDER BY id LIMIT 1")
    TradeOrderItem selectFirstItem(@Param("orderId") long orderId);

    /**
     * Loads every line of an order.
     *
     * @param orderId order id
     * @return lines in insertion order, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, order_id AS orderId, course_id AS courseId, "
                    + "owner_teacher_id AS ownerTeacherId, course_title AS courseTitle, price, "
                    + "discount_amount AS discountAmount, actual_amount AS actualAmount "
                    + "FROM trade_order_item WHERE order_id = #{orderId} AND deleted = 0 "
                    + "ORDER BY id")
    List<TradeOrderItem> selectItems(@Param("orderId") long orderId);

    /**
     * Loads the lines of several orders at once to avoid a query per row on list pages.
     *
     * @param orderIds order primary keys, must not be empty
     * @return lines ordered by order and line id, possibly empty
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "<script>SELECT id, order_id AS orderId, course_id AS courseId, "
                    + "owner_teacher_id AS ownerTeacherId, course_title AS courseTitle, price, "
                    + "discount_amount AS discountAmount, actual_amount AS actualAmount "
                    + "FROM trade_order_item WHERE deleted = 0 AND order_id IN "
                    + "<foreach collection='orderIds' item='orderId' open='(' separator=',' "
                    + "close=')'>#{orderId}</foreach> ORDER BY order_id, id</script>")
    List<TradeOrderItem> selectItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * Inserts one order and fills the generated id.
     *
     * @param order order row
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO trade_order (order_no, user_id, total_amount, discount_amount, "
                    + "pay_amount, status, coupon_id, channel, pay_expire_at, paid_at, "
                    + "idempotency_key) VALUES (#{orderNo}, #{userId}, #{totalAmount}, "
                    + "#{discountAmount}, #{payAmount}, #{status}, #{couponId}, #{channel}, "
                    + "#{payExpireAt}, #{paidAt}, #{idempotencyKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrder(TradeOrder order);

    /**
     * Inserts one order line and fills the generated id.
     *
     * @param item order line
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO trade_order_item (order_id, course_id, owner_teacher_id, course_title, "
                    + "price, discount_amount, actual_amount) VALUES (#{orderId}, #{courseId}, "
                    + "#{ownerTeacherId}, #{courseTitle}, #{price}, #{discountAmount}, "
                    + "#{actualAmount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertItem(TradeOrderItem item);

    /**
     * Counts every live order.
     *
     * @return total orders
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT COUNT(*) FROM trade_order WHERE deleted = 0")
    long countAll();

    /**
     * Counts settled orders.
     *
     * @return orders in paid status
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status = 1")
    long countPaid();

    /**
     * Sums the settled revenue of paid orders.
     *
     * @return revenue in yuan, {@code null} when nothing is paid yet
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT SUM(pay_amount) FROM trade_order WHERE deleted = 0 AND status = 1")
    BigDecimal sumPaidAmount();

    /**
     * Counts live orders matching the console filters.
     *
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param from optional inclusive creation time lower bound
     * @param to optional exclusive creation time upper bound
     * @return matching orders
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM trade_order WHERE deleted = 0 "
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='orderNo != null and orderNo != \"\"'>AND order_no LIKE "
                    + "CONCAT('%', #{orderNo}, '%') </if>"
                    + "<if test='userId != null'>AND user_id = #{userId} </if>"
                    + "<if test='from != null'>AND created_at &gt;= #{from} </if>"
                    + "<if test='to != null'>AND created_at &lt; #{to} </if></script>")
    long countForAdmin(
            @Param("status") Integer status,
            @Param("channel") Integer channel,
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Lists one page of live orders matching the console filters, newest first.
     *
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param from optional inclusive creation time lower bound
     * @param to optional exclusive creation time upper bound
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return matching order rows, amounts in yuan
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "<script>SELECT order_no AS orderNo, user_id AS userId, status, "
                    + "pay_amount AS payAmount, created_at AS createdAt "
                    + "FROM trade_order WHERE deleted = 0 "
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "<if test='channel != null'>AND channel = #{channel} </if>"
                    + "<if test='orderNo != null and orderNo != \"\"'>AND order_no LIKE "
                    + "CONCAT('%', #{orderNo}, '%') </if>"
                    + "<if test='userId != null'>AND user_id = #{userId} </if>"
                    + "<if test='from != null'>AND created_at &gt;= #{from} </if>"
                    + "<if test='to != null'>AND created_at &lt; #{to} </if>"
                    + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AdminOrderRow> selectPageForAdmin(
            @Param("status") Integer status,
            @Param("channel") Integer channel,
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * Counts orders created inside the half open time window.
     *
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @return matching orders
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT COUNT(*) FROM trade_order WHERE deleted = 0 "
                    + "AND created_at >= #{from} AND created_at < #{to}")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Counts paid orders created inside the half open time window.
     *
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @return matching paid orders
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status = 1 "
                    + "AND created_at >= #{from} AND created_at < #{to}")
    long countPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Sums the settled revenue inside the half open time window.
     *
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @return revenue in yuan, {@code null} when nothing is paid in the window
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT SUM(pay_amount) FROM trade_order WHERE deleted = 0 AND status = 1 "
                    + "AND created_at >= #{from} AND created_at < #{to}")
    BigDecimal sumPaidBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Ranks the best selling courses inside the half open time window.
     *
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @param limit maximum rows
     * @return one row per course, highest order count first
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT i.course_id AS courseId, i.course_title AS title, COUNT(*) AS orderCount "
                    + "FROM trade_order_item i INNER JOIN trade_order o ON o.id = i.order_id "
                    + "WHERE i.deleted = 0 AND o.deleted = 0 AND o.status = 1 "
                    + "AND o.created_at >= #{from} AND o.created_at < #{to} "
                    + "GROUP BY i.course_id, i.course_title "
                    + "ORDER BY COUNT(*) DESC, i.course_id ASC LIMIT #{limit}")
    List<CourseSaleRow> selectTopCourses(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit);
}
