package com.lp.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Overwrites the payer identifiers that must not outlive a deleted account.
 *
 * <p>The digest is computed by the database, not in Java, so the rewrite stays a single statement
 * that is safe to repeat: rows already carrying the {@code anon:} prefix are excluded by the {@code
 * WHERE} clause, which means a retry after a partial failure never hashes a digest twice. The
 * exclusion lives in SQL on purpose, because an application-side "read then write" would race with
 * a concurrent caller and hash the same value again.
 *
 * <p>{@code payer_openid} is {@code VARCHAR(64)}. {@code anon:} plus the first 56 hex characters of
 * SHA-256 is 61 characters, so the column width is respected without a schema change.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Mapper
public interface AccountAnonymizationMapper {

    /**
     * Rewrites the payer openid of every payment belonging to one account.
     *
     * <p>Rows marked {@code deleted} are rewritten too: a soft deleted row still holds the payer
     * identifier. The update runs in the caller's transaction, so the account row and its payments
     * become anonymous together or not at all.
     *
     * @param userId account owner
     * @param salt secret salt mixed into the digest, owned by auth-service
     * @return number of rewritten rows, {@code 0} when the account has no identified payer or every
     *     row is already anonymous
     * @author Caspian
     * @since 0.3.0
     */
    @Update(
            "UPDATE payment_record SET payer_openid = CONCAT('anon:', "
                    + "SUBSTRING(SHA2(CONCAT(#{salt}, payer_openid), 256), 1, 56)) "
                    + "WHERE order_id IN (SELECT id FROM trade_order WHERE user_id = #{userId}) "
                    + "AND payer_openid IS NOT NULL AND payer_openid NOT LIKE 'anon:%'")
    int anonymizePayerOpenid(@Param("userId") long userId, @Param("salt") String salt);
}
