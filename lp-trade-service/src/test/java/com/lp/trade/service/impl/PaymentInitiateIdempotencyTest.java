package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.common.exception.BizException;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.service.AccountDeletionGuard;
import com.lp.trade.service.PaymentService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the order row lock keeps concurrent payment initiation down to one pending attempt.
 *
 * <p>Runs against the in-memory H2 database used by the test profile so the lock is exercised by a
 * real database instead of mocks.
 *
 * @author Caspian
 * @since 0.2.0
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "lp.trade.outbox-relay-delay-ms=600000")
@ActiveProfiles("test")
class PaymentInitiateIdempotencyTest {

    private static final long USER_ID = 1001L;

    private static final long ORDER_ID = 1L;

    private static final String ORDER_NO = "LP1";

    private static final int UNCONFIGURED_GATEWAY_CODE = 50205;

    @Autowired private PaymentService paymentService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AccountDeletionGuard accountDeletionGuard;

    /**
     * Recreates the two tables and one payable order before each test.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS trade_order");
        jdbcTemplate.execute(
                "CREATE TABLE trade_order ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "order_no VARCHAR(64) NOT NULL,"
                        + "user_id BIGINT NOT NULL,"
                        + "total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL,"
                        + "coupon_id BIGINT,"
                        + "channel TINYINT NOT NULL,"
                        + "pay_expire_at DATETIME,"
                        + "paid_at DATETIME,"
                        + "closed_at DATETIME,"
                        + "idempotency_key VARCHAR(64),"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE payment_record ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "order_id BIGINT NOT NULL,"
                        + "pay_no VARCHAR(64) NOT NULL,"
                        + "channel TINYINT NOT NULL,"
                        + "pay_scene TINYINT,"
                        + "amount DECIMAL(12,2) NOT NULL,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "channel_trade_no VARCHAR(64),"
                        + "paid_at DATETIME,"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO trade_order (id, order_no, user_id, pay_amount, status, channel, "
                        + "pay_expire_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                ORDER_ID,
                ORDER_NO,
                USER_ID,
                99.00,
                0,
                PaymentChannel.ALIPAY,
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusMinutes(30)));
    }

    /**
     * Two simultaneous clicks end up with exactly one pending attempt.
     *
     * @throws Exception when the worker threads cannot finish
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void concurrentInitiateCreatesSinglePendingAttempt() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> click =
                () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        paymentService.initiate(USER_ID, "web", ORDER_NO);
                    } catch (BizException exception) {
                        // The test profile has no channel credentials; the attempt already
                        // committed.
                        assertEquals(UNCONFIGURED_GATEWAY_CODE, exception.getCode());
                    }
                    return null;
                };

        try {
            Future<Void> first = executor.submit(click);
            Future<Void> second = executor.submit(click);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Integer pending =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_record WHERE order_id = ? AND status = 0",
                        Integer.class,
                        ORDER_ID);
        assertEquals(1, pending);
    }
}
