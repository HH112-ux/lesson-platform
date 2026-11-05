package com.lp.trade.mapper;

import com.lp.trade.entity.CartItem;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads and writes the {@code cart_item} table in {@code lp_oms}.
 *
 * <p>Rows are removed physically on delete so a later re-add never conflicts with the {@code
 * uk_user_course} unique key.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface CartMapper {

    /**
     * Lists the live cart rows of a user, newest first.
     *
     * @param userId owner user id
     * @return cart rows, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, "
                    + "price_snapshot AS priceSnapshot FROM cart_item "
                    + "WHERE user_id = #{userId} AND deleted = 0 "
                    + "ORDER BY created_at DESC, id DESC")
    List<CartItem> selectByUser(@Param("userId") long userId);

    /**
     * Finds one live cart row by user and course.
     *
     * @param userId owner user id
     * @param courseId course id
     * @return cart row, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, "
                    + "price_snapshot AS priceSnapshot FROM cart_item "
                    + "WHERE user_id = #{userId} AND course_id = #{courseId} AND deleted = 0 "
                    + "LIMIT 1")
    CartItem selectByUserAndCourse(@Param("userId") long userId, @Param("courseId") long courseId);

    /**
     * Inserts one cart row. A concurrent duplicate is rejected by {@code uk_user_course}.
     *
     * @param item row to insert
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO cart_item (user_id, course_id, price_snapshot) "
                    + "VALUES (#{userId}, #{courseId}, #{priceSnapshot})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CartItem item);

    /**
     * Removes a cart row physically; deleting an absent row is a no-op.
     *
     * @param userId owner user id
     * @param courseId course id
     * @return number of removed rows, {@code 0} when the row was already absent
     * @author Caspian
     * @since 0.1.0
     */
    @Delete("DELETE FROM cart_item WHERE user_id = #{userId} AND course_id = #{courseId}")
    int deleteByUserAndCourse(@Param("userId") long userId, @Param("courseId") long courseId);
}
