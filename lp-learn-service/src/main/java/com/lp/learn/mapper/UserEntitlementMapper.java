package com.lp.learn.mapper;

import com.lp.learn.entity.UserEntitlement;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads and writes {@code user_entitlement}.
 *
 * <p>Insertion deliberately uses a plain insert: the unique key {@code uk_user_course} rejects a
 * repeated grant and the service treats that rejection as success. A pre-check would race with a
 * concurrent delivery from the trade outbox.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface UserEntitlementMapper {

    /**
     * Inserts one entitlement and fills the generated id.
     *
     * @param entitlement entitlement row
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO user_entitlement (user_id, course_id, order_id, source, granted_at, "
                    + "expire_at) VALUES (#{userId}, #{courseId}, #{orderId}, #{source}, "
                    + "#{grantedAt}, #{expireAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntitlement entitlement);

    /**
     * Counts active entitlements of one student.
     *
     * @param userId student user id
     * @return active entitlement count
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT COUNT(*) FROM user_entitlement WHERE user_id = #{userId} AND deleted = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3))")
    long countActiveByUser(@Param("userId") long userId);

    /**
     * Loads one page of active entitlements, newest first.
     *
     * @param userId student user id
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return entitlement page, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, order_id AS orderId, source, "
                    + "granted_at AS grantedAt, expire_at AS expireAt FROM user_entitlement "
                    + "WHERE user_id = #{userId} AND deleted = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "ORDER BY granted_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserEntitlement> selectActiveByUser(
            @Param("userId") long userId, @Param("offset") long offset, @Param("limit") long limit);

    /**
     * Tells whether the student may access one course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return matching active entitlement count
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT COUNT(*) FROM user_entitlement WHERE user_id = #{userId} "
                    + "AND course_id = #{courseId} AND deleted = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3))")
    long countActive(@Param("userId") long userId, @Param("courseId") long courseId);

    /**
     * Returns the subset of the given course ids the user actively owns.
     *
     * @param userId student user id
     * @param courseIds candidate course ids, must not be empty
     * @return owned course ids
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT course_id FROM user_entitlement WHERE user_id = #{userId} "
                    + "AND deleted = 0 AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach></script>")
    List<Long> selectActiveCourseIds(
            @Param("userId") long userId, @Param("courseIds") List<Long> courseIds);
}
