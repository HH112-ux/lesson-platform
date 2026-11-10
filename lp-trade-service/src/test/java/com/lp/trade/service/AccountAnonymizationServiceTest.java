package com.lp.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lp.trade.dto.AccountAnonymizationView;
import com.lp.trade.support.Sha2Alias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the payer anonymisation statement against a real database rather than mocks.
 *
 * <p>Runs on the H2 test profile in MySQL mode, so the {@code WHERE ... NOT LIKE 'anon:%'} guard,
 * the column width and the digest are all exercised as written. The alias replaces only the MySQL
 * {@code SHA2} function, which H2 does not provide.
 *
 * @author Caspian
 * @since 0.3.0
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "lp.trade.outbox-relay-delay-ms=600000")
@ActiveProfiles("test")
class AccountAnonymizationServiceTest {

    private static final long DELETED_USER_ID = 3001L;

    private static final long OTHER_USER_ID = 3002L;

    private static final String SALT = "test-salt";

    @Autowired private AccountAnonymizationService service;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates both tables with two accounts, one soft deleted payment and one payment without a
     * payer identifier.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS trade_order");
        jdbcTemplate.execute(
                "CREATE TABLE trade_order (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE payment_record (id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, "
                        + "payer_openid VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE ALIAS IF NOT EXISTS SHA2 FOR \"com.lp.trade.support.Sha2Alias.sha2\"");
        jdbcTemplate.update(
                "INSERT INTO trade_order (id, user_id) VALUES (1, ?), (2, ?), (3, ?)",
                DELETED_USER_ID,
                DELETED_USER_ID,
                OTHER_USER_ID);
        jdbcTemplate.update(
                "INSERT INTO payment_record (id, order_id, payer_openid, deleted) VALUES "
                        + "(10, 1, 'wx-openid-a', 0), (11, 2, 'wx-openid-b', 1), "
                        + "(12, 3, 'wx-openid-c', 0), (13, 1, NULL, 0)");
    }

    /**
     * Only the deleted account is rewritten, the digest fits {@code VARCHAR(64)} and a soft deleted
     * payment is covered like a live one.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rewritesOnlyTheDeletedAccountAndFitsTheColumn() throws Exception {
        AccountAnonymizationView view = service.anonymize(DELETED_USER_ID, SALT);

        assertThat(view.userId()).isEqualTo(DELETED_USER_ID);
        assertThat(view.payerOpenidRewritten()).isEqualTo(2);
        assertThat(payerOpenid(10)).isEqualTo(expectedDigest("wx-openid-a"));
        assertThat(payerOpenid(11)).isEqualTo(expectedDigest("wx-openid-b"));
        assertThat(payerOpenid(10)).hasSize(61).hasSizeLessThanOrEqualTo(64);
        assertThat(payerOpenid(12)).isEqualTo("wx-openid-c");
        assertThat(payerOpenid(13)).isNull();
    }

    /**
     * A repeated call hashes nothing twice, which is what makes the retry after a rolled-back
     * caller safe.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void repeatedCallHashesNothingTwice() throws Exception {
        service.anonymize(DELETED_USER_ID, SALT);
        String first = payerOpenid(10);

        AccountAnonymizationView second = service.anonymize(DELETED_USER_ID, SALT);

        assertThat(second.payerOpenidRewritten()).isZero();
        assertThat(payerOpenid(10)).isEqualTo(first);
    }

    private String expectedDigest(String openid) throws Exception {
        return "anon:" + Sha2Alias.sha2(SALT + openid, 256).substring(0, 56);
    }

    private String payerOpenid(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT payer_openid FROM payment_record WHERE id = ?", String.class, id);
    }
}
