package com.lp.trade.service;

import com.lp.trade.dto.AccountAnonymizationView;
import com.lp.trade.mapper.AccountAnonymizationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the payment records of a deleted account unattributable.
 *
 * <p>This is the trade half of an account cancellation. auth-service calls it inside its own
 * transaction and rolls that transaction back when this call fails, so a request stays in the
 * cooling-off table and is retried by the next scan. A retry is a no-op: the statement skips rows
 * that already carry the {@code anon:} prefix, so the window where the remote write succeeded but
 * the caller's commit failed cannot hash a value twice.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Service
public class AccountAnonymizationService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAnonymizationService.class);

    private final AccountAnonymizationMapper anonymizationMapper;

    /**
     * Creates the service with its mapper.
     *
     * @param anonymizationMapper payer identifier rewrite
     * @author Caspian
     * @since 0.3.0
     */
    public AccountAnonymizationService(AccountAnonymizationMapper anonymizationMapper) {
        this.anonymizationMapper = anonymizationMapper;
    }

    /**
     * Rewrites the payer openid of every payment of one account.
     *
     * <p>Idempotent: the second call rewrites no row and returns {@code 0}, which is a success, not
     * an error, because the caller cannot tell a retry from a first attempt.
     *
     * @param userId account owner
     * @param salt secret salt mixed into the digest, owned by auth-service
     * @return rewritten row count reported back to the caller
     * @author Caspian
     * @since 0.3.0
     */
    @Transactional
    public AccountAnonymizationView anonymize(long userId, String salt) {
        int rewritten = anonymizationMapper.anonymizePayerOpenid(userId, salt);
        LOG.info("trade account anonymized: userId={}, payerOpenidRewritten={}", userId, rewritten);
        return new AccountAnonymizationView(userId, rewritten);
    }
}
