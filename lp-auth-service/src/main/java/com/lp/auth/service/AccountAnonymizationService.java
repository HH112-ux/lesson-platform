package com.lp.auth.service;

import com.lp.auth.client.AccountAnonymizationClient;
import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.mapper.AccountAnonymizationMapper;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Makes a cancelled account unrecoverable and unattributable.
 *
 * <p>Runs inside the transaction opened by the execution job: the account row, its WeChat bindings
 * and the trade payment rows are anonymised together, and the request row only leaves the
 * cooling-off status when all three succeed. The trade call is remote, so the documented window is
 * "trade committed, local commit failed" - the next scan re-runs and every write is a no-op there.
 *
 * <p>Nothing is deleted: orders, payments, refunds, entitlements and progress rows keep their
 * counts, as the specification requires.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Service
public class AccountAnonymizationService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAnonymizationService.class);

    /** Display name the specification requires on a deleted account. */
    private static final String DELETED_NICKNAME = "已注销用户";

    private static final int RANDOM_PASSWORD_BYTES = 32;

    private final AccountAnonymizationMapper anonymizationMapper;

    private final AccountAnonymizationClient tradeClient;

    private final AccountDeletionProperties properties;

    private final PasswordEncoder passwordEncoder;

    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service with its collaborators.
     *
     * @param anonymizationMapper account and binding rewrite
     * @param tradeClient trade-side payer rewrite
     * @param properties salt used by both sides
     * @param passwordEncoder encoder for the random replacement password
     * @author Caspian
     * @since 0.3.0
     */
    public AccountAnonymizationService(
            AccountAnonymizationMapper anonymizationMapper,
            AccountAnonymizationClient tradeClient,
            AccountDeletionProperties properties,
            PasswordEncoder passwordEncoder) {
        this.anonymizationMapper = anonymizationMapper;
        this.tradeClient = tradeClient;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Anonymises one account and everything that can identify it.
     *
     * <p>The account row is written first: a row that is no longer in the cooling-off status means
     * somebody else already changed it, so the caller rolls back and the request keeps its
     * deadline.
     *
     * @param userId account owner
     * @return {@code true} when this call anonymised the account, {@code false} when the status had
     *     already moved
     * @throws com.lp.common.exception.BizException {@code 50401} when the trade call fails, which
     *     makes the caller roll back
     * @author Caspian
     * @since 0.3.0
     */
    public boolean anonymize(long userId) {
        if (anonymizationMapper.anonymizeUser(
                        userId, "deleted_" + userId, randomPasswordHash(), DELETED_NICKNAME)
                == 0) {
            return false;
        }
        String salt = properties.getAnonymizeSalt();
        int bindings = anonymizationMapper.anonymizeWechatBindings(userId, salt);
        tradeClient.anonymize(userId, salt);
        LOG.info("account anonymized: userId={}, wechatBindings={}", userId, bindings);
        return true;
    }

    private String randomPasswordHash() {
        byte[] raw = new byte[RANDOM_PASSWORD_BYTES];
        random.nextBytes(raw);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(raw));
    }
}
