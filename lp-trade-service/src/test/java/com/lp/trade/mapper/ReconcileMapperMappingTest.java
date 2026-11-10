package com.lp.trade.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the reconcile statements map snake case columns onto the entity properties.
 *
 * <p>The project does not enable MyBatis underscore to camel case mapping, so every multi-word
 * column must carry an explicit alias. Without the aliases the aggregates come back with null
 * amounts and the local rows with null channel trade numbers, which silently turns every channel
 * line into a difference.
 *
 * @author Caspian
 * @since 0.6.2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ReconcileMapperMappingTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 9);

    @Autowired private PaymentReconcileMapper reconcileMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the two tables and seeds one aggregate and one local payment.
     *
     * @author Caspian
     * @since 0.6.2
     */
    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_reconcile");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_record");
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
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE payment_record ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "pay_no VARCHAR(64),"
                        + "channel_trade_no VARCHAR(64),"
                        + "channel TINYINT NOT NULL DEFAULT 1,"
                        + "amount DECIMAL(12,2) NOT NULL DEFAULT 0,"
                        + "status TINYINT NOT NULL DEFAULT 0,"
                        + "paid_at DATETIME,"
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO payment_reconcile (channel, bill_date, bill_type, channel_count, "
                        + "channel_amount, local_count, local_amount, diff_count, diff_amount, "
                        + "status, raw_bill, deleted) VALUES (1, ?, 1, 3, 99.90, 2, 66.60, 1, 33.30, "
                        + "1, 'raw', 0)",
                BILL_DATE);
        jdbcTemplate.update(
                "INSERT INTO payment_record (id, pay_no, channel_trade_no, channel, amount, status, "
                        + "paid_at, deleted) VALUES (1, 'P-1', 'T-1', 1, 9.90, 1, ?, 0)",
                java.sql.Timestamp.valueOf(BILL_DATE.atStartOfDay().plusHours(10)));
    }

    /**
     * The aggregate comes back with bill day, counts, amounts, status and raw bill mapped.
     *
     * @author Caspian
     * @since 0.6.2
     */
    @Test
    void mapsReconcileAggregateColumns() {
        PaymentReconcile row = reconcileMapper.selectByKey(1, BILL_DATE, 1);

        assertEquals(BILL_DATE, row.getBillDate());
        assertEquals(Integer.valueOf(1), row.getBillType());
        assertEquals(Integer.valueOf(3), row.getChannelCount());
        assertEquals(new BigDecimal("99.90"), row.getChannelAmount());
        assertEquals(new BigDecimal("66.60"), row.getLocalAmount());
        assertEquals(Integer.valueOf(1), row.getDiffCount());
        assertEquals(new BigDecimal("33.30"), row.getDiffAmount());
        assertEquals(Integer.valueOf(1), row.getStatus());
        assertEquals("raw", row.getRawBill());
    }

    /**
     * A local payment keeps its channel trade number, which the difference list matches on.
     *
     * @author Caspian
     * @since 0.6.2
     */
    @Test
    void mapsLocalPaymentChannelTradeNumber() {
        PaymentRecord record =
                reconcileMapper
                        .selectLocalPayments(
                                1, BILL_DATE.atStartOfDay(), BILL_DATE.plusDays(1).atStartOfDay())
                        .get(0);

        assertEquals("T-1", record.getChannelTradeNo());
        assertEquals("P-1", record.getPayNo());
        assertEquals(new BigDecimal("9.90"), record.getAmount());
    }
}
