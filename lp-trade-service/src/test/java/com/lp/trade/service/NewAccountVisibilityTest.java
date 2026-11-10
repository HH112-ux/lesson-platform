package com.lp.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lp.trade.mapper.TradeOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins the trade half of AC-16: after the same WeChat openid is bound to a new account, the buyer
 * order query used by the order list only sees rows owned by the id it is called with.
 *
 * <p>Rebinding anonymises the old account and inserts a new one, so every order keeps the old user
 * id (see {@code AuthServiceImplTest#anonymizedOpenidRegistersANewAccountThatCannotSeeTheOldOne}).
 * The new id therefore has to come back empty; the old id keeps its history.
 *
 * @author Caspian
 * @since 0.3.0
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "lp.trade.outbox-relay-delay-ms=600000",
            "lp.trade.order-timeout-initial-delay-ms=600000"
        })
@ActiveProfiles("test")
class NewAccountVisibilityTest {

    private static final long OLD_USER_ID = 2001L;

    private static final long NEW_USER_ID = 2002L;

    @Autowired private TradeOrderMapper tradeOrderMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the order table with one paid order that belongs to the old account.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS trade_order");
        jdbcTemplate.execute(
                "CREATE TABLE trade_order ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "order_no VARCHAR(64) NOT NULL,"
                        + "user_id BIGINT NOT NULL,"
                        + "total_amount INT NOT NULL DEFAULT 0,"
                        + "discount_amount INT NOT NULL DEFAULT 0,"
                        + "pay_amount INT NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL,"
                        + "coupon_id BIGINT,"
                        + "channel VARCHAR(16),"
                        + "pay_expire_at DATETIME,"
                        + "paid_at DATETIME,"
                        + "closed_at DATETIME,"
                        + "idempotency_key VARCHAR(64),"
                        + "created_at DATETIME,"
                        + "cancel_reason VARCHAR(200),"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO trade_order (order_no, user_id, status, deleted) VALUES (?, ?, 2, 0)",
                "SO2001",
                OLD_USER_ID);
    }

    /**
     * Counts and pages the order table exactly the way the buyer order list does.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void newAccountOfTheSameOpenidSeesNoOrdersOfTheOldOne() {
        assertThat(tradeOrderMapper.countByUser(OLD_USER_ID, null)).isEqualTo(1L);
        assertThat(tradeOrderMapper.countByUser(NEW_USER_ID, null)).isZero();
        assertThat(tradeOrderMapper.selectPageByUser(NEW_USER_ID, null, 0, 20)).isEmpty();
    }
}
