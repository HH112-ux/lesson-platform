package com.lp.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads order state needed by trade features.
 *
 * <p>Only the ownership probe exists for now; order creation and cancel queries are added in the
 * order step.
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
}
