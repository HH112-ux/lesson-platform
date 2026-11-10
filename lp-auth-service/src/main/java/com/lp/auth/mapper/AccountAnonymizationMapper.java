package com.lp.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Rewrites the personal data of an account whose cooling-off period has ended.
 *
 * <p>Both statements are idempotent and conditional, so a retried execution - the request row may
 * return to {@code status = 0} when a caller rolls back - never writes a second time: the account
 * update matches only an account still in the cooling-off status, and the binding update skips rows
 * that already carry the {@code anon:} prefix.
 *
 * <p>The WeChat digest is computed by the database for the same reason as the trade side: the guard
 * has to sit in the statement, not in an application side read-then-write.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Mapper
public interface AccountAnonymizationMapper {

    /**
     * Replaces every personal column of an account and moves it to the deleted status.
     *
     * <p>Statuses are inlined because MyBatis annotations cannot reference Java constants: {@code
     * 2} is the cooling-off status and {@code 3} the deleted one. Email, phone and avatar are
     * cleared so their unique keys are released and the same person can register again.
     *
     * @param userId authentic user id
     * @param username replacement login name, unique because it embeds the user id
     * @param passwordHash BCrypt hash of a random secret nobody knows
     * @param nickname display name required for a deleted account
     * @return 1 when the account was still in the cooling-off status, 0 when it already moved
     * @author Caspian
     * @since 0.3.0
     */
    @Update(
            "UPDATE sys_user SET status = 3, username = #{username}, "
                    + "password_hash = #{passwordHash}, nickname = #{nickname}, gender = 0, "
                    + "avatar_url = NULL, email = NULL, phone = NULL "
                    + "WHERE id = #{userId} AND deleted = 0 AND status = 2")
    int anonymizeUser(
            @Param("userId") long userId,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("nickname") String nickname);

    /**
     * Unbinds every WeChat account of a user and replaces each openid with a digest.
     *
     * <p>{@code openid} is {@code VARCHAR(128)} and {@code anon:} plus 64 hex characters is 69, so
     * the full digest fits. {@code uk_openid} stays unique because the digest is deterministic per
     * openid; once the original value is gone the same WeChat account can bind to a new user.
     *
     * @param userId authentic user id
     * @param salt secret salt mixed into the digest
     * @return number of bindings rewritten, {@code 0} when the account never bound WeChat
     * @author Caspian
     * @since 0.3.0
     */
    @Update(
            "UPDATE sys_user_wechat SET deleted = 1, unionid = NULL, session_key = NULL, "
                    + "openid = CONCAT('anon:', SHA2(CONCAT(#{salt}, openid), 256)) "
                    + "WHERE user_id = #{userId} AND deleted = 0 AND openid NOT LIKE 'anon:%'")
    int anonymizeWechatBindings(@Param("userId") long userId, @Param("salt") String salt);
}
