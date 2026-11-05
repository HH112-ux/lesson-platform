package com.lp.trade.mapper;

import com.lp.trade.entity.Coupon;
import com.lp.trade.entity.UserCoupon;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads coupon templates and consumes user coupons.
 *
 * <p>Consumption is a conditional update on {@code status = 0}; a return value of {@code 0} means
 * another request already used the coupon and the caller must fail the whole order transaction.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface CouponMapper {

    /**
     * Loads the user coupon row of one user and template.
     *
     * @param userId owner user id
     * @param couponId coupon template id
     * @return user coupon, or {@code null} when not received
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, coupon_id AS couponId, status, "
                    + "order_id AS orderId, used_at AS usedAt FROM user_coupon "
                    + "WHERE user_id = #{userId} AND coupon_id = #{couponId} "
                    + "AND deleted = 0 LIMIT 1")
    UserCoupon selectUserCoupon(@Param("userId") long userId, @Param("couponId") long couponId);

    /**
     * Loads one coupon template.
     *
     * @param couponId coupon template id
     * @return template, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, code, name, type, threshold_amount AS thresholdAmount, "
                    + "discount_amount AS discountAmount, discount_rate AS discountRate, "
                    + "start_at AS startAt, end_at AS endAt, status FROM coupon "
                    + "WHERE id = #{couponId} AND deleted = 0 LIMIT 1")
    Coupon selectCoupon(@Param("couponId") long couponId);

    /**
     * Marks a user coupon as used by an order.
     *
     * <p>The {@code status = 0} condition makes the update idempotent and race free.
     *
     * @param userCouponId user coupon primary key
     * @param orderId consuming order id
     * @param usedAt usage time
     * @return affected rows, {@code 0} when the coupon was already used
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE user_coupon SET status = 1, order_id = #{orderId}, used_at = #{usedAt} "
                    + "WHERE id = #{userCouponId} AND status = 0")
    int markUsed(
            @Param("userCouponId") long userCouponId,
            @Param("orderId") long orderId,
            @Param("usedAt") LocalDateTime usedAt);
}
