package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.trade.service.AccountDeletionBlockerQuery;
import java.time.LocalDateTime;
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

/**
 * Proves the timeout close is safe under a real database and against a paid order.
 *
 * <p>Two workers race on the same order; the conditional status update must let exactly one win.
 * The second test pins the callback-first ordering: an order already paid stays paid and its
 * payment record and coupon are left untouched. The last test proves the close also releases the
 * account deletion blocker, which is what lets a user apply for cancellation again.
 *
 * @author Caspian
 * @since 0.2.0
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "lp.trade.outbox-relay-delay-ms=600000",
            "lp.trade.order-timeout-initial-delay-ms=600000"
        })
@ActiveProfiles("test")
class OrderTimeoutConcurrencyTest {

    private static final long ORDER_ID = 11L;

    private static final long USER_ID = 1001L;

    @Autowired private OrderTimeoutJob orderTimeoutJob;

    @Autowired private AccountDeletionBlockerQuery blockerQuery;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the four tables with one expired order, one pending payment and one used coupon.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_coupon");
        jdbcTemplate.execute("DROP TABLE IF EXISTS refund_request");
        jdbcTemplate.execute("DROP TABLE IF EXISTS trade_order");
        jdbcTemplate.execute(
                "CREATE TABLE trade_order ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id BIGINT NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL,"
                        + "pay_expire_at DATETIME,"
                        + "closed_at DATETIME,"
                        + "cancel_reason VARCHAR(200),"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE refund_request ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id BIGINT NOT NULL,"
                        + "order_id BIGINT NOT NULL,"
                        + "status TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE payment_record ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "order_id BIGINT NOT NULL,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE user_coupon ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id BIGINT NOT NULL,"
                        + "coupon_id BIGINT NOT NULL,"
                        + "status TINYINT NOT NULL,"
                        + "order_id BIGINT,"
                        + "used_at DATETIME,"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO trade_order (id, user_id, status, pay_expire_at, deleted) "
                        + "VALUES (?, ?, 0, ?, 0)",
                ORDER_ID,
                USER_ID,
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));
        jdbcTemplate.update(
                "INSERT INTO payment_record (id, order_id, status, deleted) VALUES (1, ?, 0, 0)",
                ORDER_ID);
        jdbcTemplate.update(
                "INSERT INTO user_coupon (id, user_id, coupon_id, status, order_id, used_at, "
                        + "deleted) VALUES (1, 1001, 501, 1, ?, ?, 0)",
                ORDER_ID,
                java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * Two concurrent closes let exactly one worker close the order and its dependent rows.
     *
     * @throws Exception when the worker threads cannot finish
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void concurrentCloseClosesExactlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> close =
                () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return orderTimeoutJob.closeOne(ORDER_ID, LocalDateTime.now());
                };
        Future<Boolean> first = executor.submit(close);
        Future<Boolean> second = executor.submit(close);
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        boolean firstResult = first.get(10, TimeUnit.SECONDS);
        boolean secondResult = second.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(firstResult ^ secondResult);
        assertEquals(3, status("trade_order"));
        assertEquals("payment timeout", value("SELECT cancel_reason FROM trade_order"));
        assertEquals(2, status("payment_record"));
        assertEquals(0, status("user_coupon"));
        assertNull(value("SELECT order_id FROM user_coupon"));
    }

    /**
     * A paid order is never closed and keeps its pending payment and occupied coupon.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void paidOrderIsNotClosed() {
        jdbcTemplate.update("UPDATE trade_order SET status = 1 WHERE id = ?", ORDER_ID);

        assertFalse(orderTimeoutJob.closeOne(ORDER_ID, LocalDateTime.now()));

        assertEquals(1, status("trade_order"));
        assertEquals(0, status("payment_record"));
        assertEquals(1, status("user_coupon"));
    }

    /**
     * AC-9: a closed unfinished order stops blocking the account deletion request.
     *
     * <p>The order is seeded in the status the blocker counts ({@code pending}) and the job's own
     * close path flips it to the timed-out status; the close time is passed explicitly, so the test
     * does not depend on the wall clock.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void timedOutOrderNoLongerBlocksAccountDeletion() {
        assertTrue(blockerQuery.blockers(USER_ID).blocked());

        assertTrue(orderTimeoutJob.closeOne(ORDER_ID, LocalDateTime.now()));

        assertEquals(3, status("trade_order"));
        assertFalse(blockerQuery.blockers(USER_ID).blocked());
    }

    private int status(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT status FROM " + table, Integer.class);
        return value == null ? -1 : value;
    }

    private Object value(String sql) {
        return jdbcTemplate.queryForObject(sql, Object.class);
    }
}
