package com.lp.auth.mapper;

import com.lp.auth.entity.AdminUserRow;
import com.lp.auth.entity.UserAccount;
import com.lp.auth.entity.UserWechat;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and updates the account tables owned by the {@code lp_ums} schema.
 *
 * <p>Column aliases keep the mapping independent of the global underscore setting. Every lookup
 * filters on {@code deleted = 0} so a logically deleted account can never log in.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface AuthUserMapper {

    /**
     * Finds an active account by login name.
     *
     * @param account login name supplied by the client
     * @return account row, or {@code null} when absent or deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, username, password_hash AS passwordHash, nickname, "
                    + "avatar_url AS avatarUrl, status FROM sys_user "
                    + "WHERE username = #{account} AND deleted = 0 LIMIT 1")
    UserAccount selectByUsername(@Param("account") String account);

    /**
     * Finds a live account by id, used when refreshing a session.
     *
     * @param userId authentic user id taken from the stored refresh session
     * @return account row, or {@code null} when absent or deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, username, password_hash AS passwordHash, nickname, "
                    + "avatar_url AS avatarUrl, email, phone, status FROM sys_user "
                    + "WHERE id = #{userId} AND deleted = 0 LIMIT 1")
    UserAccount selectById(@Param("userId") long userId);

    /**
     * Finds a live WeChat binding by openid.
     *
     * <p>A logically deleted binding is ignored so a reused openid can be bound again.
     *
     * @param openid WeChat openid resolved from {@code jscode2session}
     * @return binding row, or {@code null} when absent or deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT user_id AS userId, openid, unionid, session_key AS sessionKey "
                    + "FROM sys_user_wechat WHERE openid = #{openid} AND deleted = 0 LIMIT 1")
    UserWechat selectByOpenid(@Param("openid") String openid);

    /**
     * Returns enabled role codes granted to the user.
     *
     * @param userId authentic user id
     * @return role codes ordered by role id, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT r.code FROM sys_role r INNER JOIN sys_user_role ur ON ur.role_id = r.id "
                    + "WHERE ur.user_id = #{userId} AND ur.deleted = 0 AND r.deleted = 0 "
                    + "AND r.status = 1 ORDER BY r.id")
    List<String> selectRoleCodes(@Param("userId") long userId);

    /**
     * Returns enabled permission codes reachable through the user's roles.
     *
     * <p>Permissions come from {@code sys_menu.permission_code}; disabled menus and deleted role
     * links are excluded so a revoked permission disappears from the next token.
     *
     * @param userId authentic user id
     * @return permission codes ordered by code, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT DISTINCT m.permission_code FROM sys_menu m "
                    + "INNER JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.deleted = 0 "
                    + "INNER JOIN sys_role r ON r.id = rm.role_id AND r.deleted = 0 AND r.status = 1 "
                    + "INNER JOIN sys_user_role ur ON ur.role_id = r.id AND ur.deleted = 0 "
                    + "WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1 "
                    + "AND m.permission_code IS NOT NULL ORDER BY m.permission_code")
    List<String> selectPermissionCodes(@Param("userId") long userId);

    /**
     * Inserts a new account and fills the generated id back into the entity.
     *
     * <p>{@code status} and {@code gender} fall back to their schema defaults, so a registered
     * account is active from the moment it exists. A duplicate login name, email or phone still
     * violates a unique key and surfaces as a duplicate key exception.
     *
     * @param account account row carrying the encoded password
     * @return number of inserted rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO sys_user (username, password_hash, nickname, email, phone) "
                    + "VALUES (#{username}, #{passwordHash}, #{nickname}, #{email}, #{phone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserAccount account);

    /**
     * Finds an enabled role id by its code.
     *
     * @param code role code such as {@code STUDENT}
     * @return role id, or {@code null} when the role is absent or disabled
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id FROM sys_role WHERE code = #{code} AND deleted = 0 "
                    + "AND status = 1 LIMIT 1")
    Long selectRoleIdByCode(@Param("code") String code);

    /**
     * Grants a role to a user.
     *
     * @param userId user id
     * @param roleId role id
     * @return number of inserted rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    /**
     * Binds a WeChat openid to an account.
     *
     * <p>The session key is persisted for server-side use only; it is never logged or returned.
     *
     * @param binding binding row with user id, openid, unionid and session key
     * @return number of inserted rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO sys_user_wechat (user_id, openid, unionid, session_key) "
                    + "VALUES (#{userId}, #{openid}, #{unionid}, #{sessionKey})")
    int insertWechatBinding(UserWechat binding);

    /**
     * Records the successful login time.
     *
     * @param userId authentic user id
     * @return number of updated rows
     * @author Caspian
     * @since 0.1.0
     */
    @Update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = #{userId}")
    int touchLastLogin(@Param("userId") long userId);

    /**
     * Switches the account status only when it still holds the expected value.
     *
     * <p>The conditional update keeps a repeated or racing cancellation request from overwriting a
     * status written by another transition, such as the execution job.
     *
     * @param userId authentic user id
     * @param expected status the row must currently hold
     * @param next status to write
     * @return 1 when the row matched, 0 when it was already changed or absent
     * @author Caspian
     * @since 0.2.0
     */
    @Update(
            "UPDATE sys_user SET status = #{next} "
                    + "WHERE id = #{userId} AND deleted = 0 AND status = #{expected}")
    int updateStatusIfCurrent(
            @Param("userId") long userId, @Param("expected") int expected, @Param("next") int next);

    /**
     * Counts every live account.
     *
     * @return total accounts that are not logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countUsers();

    /**
     * Counts accounts registered at or after the given instant.
     *
     * <p>The caller passes the boundary so the statement stays portable across MySQL and the H2
     * test database.
     *
     * @param since inclusive lower bound of {@code created_at}
     * @return matching live accounts
     * @author Caspian
     * @since 0.2.0
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND created_at >= #{since}")
    long countCreatedSince(@Param("since") LocalDateTime since);

    /**
     * Counts live accounts matching the console filters.
     *
     * @param keyword matches username, nickname or phone; {@code null} or blank matches all
     * @param status account status filter, {@code null} for all statuses
     * @return matching accounts
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM sys_user WHERE deleted = 0 "
                    + "<if test='keyword != null and keyword != \"\"'>AND (username LIKE "
                    + "CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%') "
                    + "OR phone LIKE CONCAT('%', #{keyword}, '%')) </if>"
                    + "<if test='status != null'>AND status = #{status} </if></script>")
    long countForAdmin(@Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * Lists one page of live accounts, newest registration first.
     *
     * @param keyword matches username, nickname or phone; {@code null} or blank matches all
     * @param status account status filter, {@code null} for all statuses
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return matching account rows
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "<script>SELECT id AS userId, username, nickname, phone, status, "
                    + "created_at AS createdAt FROM sys_user WHERE deleted = 0 "
                    + "<if test='keyword != null and keyword != \"\"'>AND (username LIKE "
                    + "CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%') "
                    + "OR phone LIKE CONCAT('%', #{keyword}, '%')) </if>"
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AdminUserRow> selectPageForAdmin(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
