package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
 * <p>Both workers wait on a barrier, find no batch and race on {@code uk_active_day}. Whichever way
 * the database reports the overlap - a unique-key collision or a lock wait - exactly one row must
 * remain and the losing caller must report an incomplete bill day ({@code 62019}). The test asserts
 * that outcome instead of requiring one particular mechanism, so a serially executed pair and a
 * real collision are both legal. A second test proves that a voided day is closed again as a new
 * row while the voided row stays as history. These are persistence-level guarantees and are
 * therefore exercised against a real schema rather than a mocked mapper.
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
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_outbox");
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
                        + "active_day_key VARCHAR(64),"
                        + "CONSTRAINT uk_active_day UNIQUE (active_day_key))");
        jdbcTemplate.execute(
                "CREATE TABLE audit_outbox ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "dedupe_key VARCHAR(128) NOT NULL,"
                        + "operator_id BIGINT NOT NULL,"
                        + "operator_role TINYINT NOT NULL,"
                        + "action_type TINYINT NOT NULL,"
                        + "object_type TINYINT NOT NULL,"
                        + "object_id BIGINT NOT NULL,"
                        + "from_status TINYINT NOT NULL,"
                        + "to_status TINYINT NOT NULL,"
                        + "remark VARCHAR(300),"
                        + "trace_id VARCHAR(64) NOT NULL,"
                        + "occurred_at DATETIME NOT NULL,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "retry_count INT NOT NULL DEFAULT 0,"
                        + "next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "delivered_at DATETIME,"
                        + "last_error VARCHAR(300),"
                        + "CONSTRAINT uk_dedupe UNIQUE (dedupe_key))");
        insertBill(1, "100.00", 2);
        insertBill(2, "30.00", 1);
    }

    /**
     * Two concurrent closes leave one batch, and the losing caller reports {@code 62019}.
     *
     * @throws Exception when the worker threads cannot finish
     * @author Caspian
     * @since 0.6.2
     */
    @Test
    void concurrentCloseKeepsOneBatchAndRejectsLoser() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Long> close =
                () -> {
                    start.await(5, TimeUnit.SECONDS);
                    return settlementService.close(1, BILL_DATE, CLOSED_BY).getId();
                };
        Future<Long> first = executor.submit(close);
        Future<Long> second = executor.submit(close);
        List<Long> winners = new ArrayList<>();
        List<RuntimeException> losers = new ArrayList<>();
        for (Future<Long> future : List.of(first, second)) {
            try {
                winners.add(future.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException ex) {
                assertInstanceOf(RuntimeException.class, ex.getCause());
                losers.add((RuntimeException) ex.getCause());
            }
        }
        executor.shutdown();
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_settle_batch", Integer.class),
                "two concurrent closes must keep exactly one batch");
        assertFalse(winners.isEmpty(), "at least one concurrent close must succeed");
        for (RuntimeException loser : losers) {
            assertInstanceOf(BizException.class, loser);
            assertEquals(
                    ErrorCode.SETTLE_BILL_INCOMPLETE.getCode(), ((BizException) loser).getCode());
        }
        if (winners.size() == 2) {
            assertEquals(winners.get(0), winners.get(1), "a day closed twice keeps one id");
        }
    }

    /**
     * A void unlocks the day and the next close writes a new row, leaving the voided row intact.
     *
     * @author Caspian
     * @since 0.6.2
     */
    @Test
    void voidedDayIsClosedAgainAsANewBatchRow() {
        Long firstId = settlementService.close(1, BILL_DATE, CLOSED_BY).getId();
        settlementService.voidSettleBatch(firstId, 9002L, "duplicate bill import", "trace-void-1");
        Long secondId = settlementService.close(1, BILL_DATE, CLOSED_BY).getId();

        assertNotEquals(firstId, secondId);
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_settle_batch", Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_settle_batch WHERE status = 1",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_settle_batch WHERE status = 2",
                        Integer.class));
        assertEquals(
                "duplicate bill import",
                jdbcTemplate.queryForObject(
                        "SELECT remark FROM audit_outbox WHERE object_id = ?",
                        String.class,
                        firstId));
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
