package com.lp.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Counts the trade rows that block an account cancellation.
 *
 * <p>Only reads; the account cancellation itself never writes to {@code lp_oms}. The status sets
 * match {@code OrderStatus} ({@code 0} pending, {@code 4} refunding) and {@code RefundStatus}
 * ({@code 0} pending review, {@code 1} approved, {@code 5} unknown result): a refund that is still
 * moving money must not lose its payer to anonymisation. Statuses are inlined because MyBatis
 * annotations cannot reference Java constants.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Mapper
public interface AccountDeletionBlockerMapper {

    /**
     * Counts orders of the user that are unpaid or waiting for a refund decision.
     *
     * @param userId account owner
     * @return number of blocking orders
     * @author Caspian
     * @since 0.2.0
     */
    @Select("SELECT COUNT(*) FROM trade_order WHERE user_id = #{userId} AND status IN (0, 4)")
    int countBlockingOrders(@Param("userId") long userId);

    /**
     * Counts refund requests of the user that are not finished yet.
     *
     * @param userId account owner
     * @return number of blocking refund requests
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT COUNT(*) FROM refund_request WHERE user_id = #{userId} "
                    + "AND status IN (0, 1, 5)")
    int countBlockingRefunds(@Param("userId") long userId);
}
