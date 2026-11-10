package com.lp.auth.mapper;

import com.lp.auth.entity.UserAccountCancellation;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Reads and writes the account cancellation requests owned by the {@code lp_ums} schema.
 *
 * <p>Column aliases keep the mapping independent of the global underscore setting. Withdrawals use
 * a conditional update on {@code status = 0} so two concurrent requests cannot both win.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Mapper
public interface UserAccountCancellationMapper {

    /**
     * Finds the cooling-off request of a user.
     *
     * @param userId authentic user id
     * @return active request, or {@code null} when the user has none
     * @author Caspian
     * @since 0.2.0
     */
    @Select(
            "SELECT id, user_id AS userId, status, requested_at AS requestedAt, "
                    + "effective_at AS effectiveAt, withdrawn_at AS withdrawnAt, "
                    + "executed_at AS executedAt, created_at AS createdAt, "
                    + "updated_at AS updatedAt FROM user_account_cancellation "
                    + "WHERE user_id = #{userId} AND status = 0 LIMIT 1")
    UserAccountCancellation selectActiveByUserId(@Param("userId") long userId);

    /**
     * Inserts a cooling-off request.
     *
     * @param request request carrying user id, requested time and fixed effective time
     * @return number of inserted rows
     * @author Caspian
     * @since 0.2.0
     */
    @Insert(
            "INSERT INTO user_account_cancellation (user_id, status, requested_at, effective_at) "
                    + "VALUES (#{userId}, 0, #{requestedAt}, #{effectiveAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccountCancellation request);

    /**
     * Withdraws an active request.
     *
     * @param id request id
     * @param withdrawnAt withdrawal time
     * @return 1 when the request was still active, 0 when it was already withdrawn or executed
     * @author Caspian
     * @since 0.2.0
     */
    @Update(
            "UPDATE user_account_cancellation SET status = 1, withdrawn_at = #{withdrawnAt} "
                    + "WHERE id = #{id} AND status = 0")
    int withdraw(@Param("id") long id, @Param("withdrawnAt") LocalDateTime withdrawnAt);
}
