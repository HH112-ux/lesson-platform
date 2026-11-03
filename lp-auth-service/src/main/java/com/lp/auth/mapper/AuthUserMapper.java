package com.lp.auth.mapper;

import com.lp.auth.entity.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
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
     * Records the successful login time.
     *
     * @param userId authentic user id
     * @return number of updated rows
     * @author Caspian
     * @since 0.1.0
     */
    @Update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = #{userId}")
    int touchLastLogin(@Param("userId") long userId);
}
