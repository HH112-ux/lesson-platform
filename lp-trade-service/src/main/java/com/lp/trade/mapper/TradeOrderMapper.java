package com.lp.trade.mapper;

import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import java.time.LocalDateTime;
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
}
