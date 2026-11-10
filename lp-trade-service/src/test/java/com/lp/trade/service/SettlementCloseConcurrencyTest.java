package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
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
 * Proves a settle close keeps exactly one batch row under two concurrent callers.
 *
 * <p>Both workers read the same day, find no batch and race on {@code uk_channel_date}. The
 * database must let one insert win and make the other statement converge on the same row, so the
 * two callers return the same primary key and the table holds one row. This is a persistence-level
 * guarantee and is therefore exercised against a real schema rather than a mocked mapper.
 *
 * @author Caspian
 * @since 0.6.2
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "lp.trade.outbox-relay-delay-ms=600000",
            "lp.trade.order-timeout-initial-delay-ms=600000"
        })
@ActiveProfiles("test")
class SettlementCloseConcurrencyTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 9);

    private static final long CLOSED_BY = 9001L;

    @Autowired private SettlementService settlementService;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the two tables and seeds one matched payment bill and one matched refund bill.
     *
     * @author Caspian
     * @since 0.6.2
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_settle_batch");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_reconcile");
        jdbcTemplate.execute(
                "CREATE TABLE payment_reconcile ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "channel TINYINT NOT NULL,"
                        + "bill_date DATE NOT NULL,"
                        + "bill_type TINYINT NOT NULL,"
                        + "channel_count INT NOT NULL DEFAULT 0,"
                        + "channel_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "local_count INT NOT NULL DEFAULT 0,"
                        + "local_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "diff_count INT NOT NULL DEFAULT 0,"
                        + "diff_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "handled_by BIGINT,"
                        + "handled_at DATETIME,"
                        + "raw_bill CLOB,"
                        + "diff_detail CLOB,"
                        + "deleted TINYINT NOT NULL DEFAULT 0,"
                        + "CONSTRAINT uk_channel_date_type UNIQUE (channel, bill_date, bill_type))");
        jdbcTemplate.execute(
                "CREATE TABLE payment_settle_batch ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "channel TINYINT NOT NULL,"
                        + "bill_date DATE NOT NULL,"
                        + "channel_pay_count INT NOT NULL DEFAULT 0,"
                        + "channel_pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "channel_refund_count INT NOT NULL DEFAULT 0,"
                        + "channel_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "local_pay_count INT NOT NULL DEFAULT 0,"
                        + "local_pay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "local_refund_count INT NOT NULL DEFAULT 0,"
                        + "local_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "net_channel_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "net_local_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "diff_count INT NOT NULL DEFAULT 0,"
                        + "diff_amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL DEFAULT 1,"
                        + "closed_by BIGINT NOT NULL,"
                        + "closed_at DATETIME NOT NULL,"
                        + "void_by BIGINT,"
                        + "void_at DATETIME,"
                        + "CONSTRAINT uk_channel_date UNIQUE (channel, bill_date))");
        insertBill(1, "100.00", 2);
        insertBill(2, "30.00", 1);
    }

    /**
     * Two concurrent closes return the same batch id and leave one row.
     *
     * @throws Exception when the worker threads cannot finish
     * @author Caspian
     * @since 0.6.2
     */
    @Test
    void concurrentCloseKeepsOneBatch() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> close =
                () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return settlementService.close(1, BILL_DATE, CLOSED_BY).getId();
                };
        Future<Long> first = executor.submit(close);
        Future<Long> second = executor.submit(close);
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        Long firstId = first.get(10, TimeUnit.SECONDS);
        Long secondId = second.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(firstId, secondId);
        Integer rows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_settle_batch", Integer.class);
        assertEquals(1, rows);
    }

    private void insertBill(int billType, String channelAmount, int count) {
        jdbcTemplate.update(
                "INSERT INTO payment_reconcile (channel, bill_date, bill_type, channel_count, "
                        + "channel_amount, local_count, local_amount, status, deleted) "
                        + "VALUES (1, ?, ?, ?, ?, ?, ?, 1, 0)",
                BILL_DATE,
                billType,
                count,
                channelAmount,
                count,
                channelAmount);
    }
}
