package com.lp.learn.mapper;

import com.lp.learn.entity.UserEntitlement;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes {@code user_entitlement}.
 *
 * <p>Insertion deliberately uses a plain insert: the unique key {@code uk_user_course} rejects a
 * repeated grant and the service treats that rejection as success. A pre-check would race with a
 * concurrent delivery from the trade outbox.
 *
 * <p>Only rows with {@code status = 0} grant access. A refund flips the row to {@code status = 1}
 * and keeps it, so learning records stay readable and a later purchase can reactivate the same row.
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
            "INSERT INTO user_entitlement (user_id, course_id, order_id, source, status, granted_at, "
                    + "revoked_at, expire_at) VALUES (#{userId}, #{courseId}, #{orderId}, #{source}, "
                    + "#{status}, #{grantedAt}, #{revokedAt}, #{expireAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntitlement entitlement);

    /**
     * Counts every entitlement of one student, including refunded ones.
     *
     * @param userId student user id
     * @return entitlement count
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT COUNT(*) FROM user_entitlement WHERE user_id = #{userId} AND deleted = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3))")
    long countAllByUser(@Param("userId") long userId);

    /**
     * Loads one page of entitlements including refunded ones, active first.
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
                    + "status, granted_at AS grantedAt, revoked_at AS revokedAt, "
                    + "expire_at AS expireAt FROM user_entitlement "
                    + "WHERE user_id = #{userId} AND deleted = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "ORDER BY status ASC, granted_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserEntitlement> selectAllByUser(
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
                    + "AND course_id = #{courseId} AND deleted = 0 AND status = 0 "
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
                    + "AND deleted = 0 AND status = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach></script>")
    List<Long> selectActiveCourseIds(
            @Param("userId") long userId, @Param("courseIds") List<Long> courseIds);

    /**
     * Locks the entitlement row of one student and course for a grant or revoke decision.
     *
     * @param userId student user id
     * @param courseId course id
     * @return the row, or {@code null} when the student never owned the course
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, order_id AS orderId, source, "
                    + "status, granted_at AS grantedAt, revoked_at AS revokedAt, "
                    + "expire_at AS expireAt FROM user_entitlement "
                    + "WHERE user_id = #{userId} AND course_id = #{courseId} AND deleted = 0 "
                    + "FOR UPDATE")
    UserEntitlement selectByUserCourseForUpdate(
            @Param("userId") long userId, @Param("courseId") long courseId);

    /**
     * Reactivates a refunded row for a new purchase.
     *
     * @param id entitlement primary key
     * @param orderId new order id, may be {@code null} for operations-issued access
     * @param source 1 purchase, 2 operations grant, 3 redemption
     * @param grantedAt new grant time
     * @return affected rows, {@code 0} when the row was not revoked
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE user_entitlement SET status = 0, order_id = #{orderId}, source = #{source}, "
                    + "granted_at = #{grantedAt}, revoked_at = NULL, expire_at = NULL "
                    + "WHERE id = #{id} AND status = 1")
    int reactivate(
            @Param("id") long id,
            @Param("orderId") Long orderId,
            @Param("source") int source,
            @Param("grantedAt") LocalDateTime grantedAt);

    /**
     * Revokes access of an active row while keeping the row and its learning records.
     *
     * @param id entitlement primary key
     * @param revokedAt revoke time
     * @return affected rows, {@code 0} when the row was already revoked
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE user_entitlement SET status = 1, revoked_at = #{revokedAt} "
                    + "WHERE id = #{id} AND status = 0")
    int revoke(@Param("id") long id, @Param("revokedAt") LocalDateTime revokedAt);
}
