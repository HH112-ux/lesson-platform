package com.lp.trade.reconcile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReconcileBillCsvParser}. */
class ReconcileBillCsvParserTest {

    private final ReconcileBillCsvParser parser = new ReconcileBillCsvParser();

    @Test
    void parsesSampleBill() throws IOException {
        List<ReconcileBillLine> lines = parser.parse(readSample());

        assertEquals(2, lines.size());
        assertEquals("2026111000001", lines.get(0).tradeNo());
        assertEquals("LP202611100000001", lines.get(0).merchantOrderNo());
        assertEquals(new BigDecimal("9.90"), lines.get(0).amount());
        assertEquals(LocalDateTime.of(2026, 11, 10, 10, 0, 0), lines.get(0).occurredAt());
    }

    @Test
    void stripsUtf8Bom() {
        String csv = "\uFEFFtradeNo,merchantOrderNo,amount,occurredAt\n" + "T1,,9.90,\n";

        assertEquals(1, parser.parse(csv).size());
    }

    @Test
    void rejectsDuplicateTradeNo() {
        String csv = "tradeNo,merchantOrderNo,amount,occurredAt\n" + "T1,,9.90,\n" + "T1,,9.90,\n";

        assertThrows(BizException.class, () -> parser.parse(csv));
    }

    @Test
    void rejectsBadHeader() {
        String csv = "tradeNo,amount\nT1,9.90\n";

        assertThrows(BizException.class, () -> parser.parse(csv));
    }

    @Test
    void rejectsNegativeOrExcessScaleAmount() {
        assertThrows(
                BizException.class,
                () -> parser.parse("tradeNo,merchantOrderNo,amount,occurredAt\nT1,,-1.00,\n"));
        assertThrows(
                BizException.class,
                () -> parser.parse("tradeNo,merchantOrderNo,amount,occurredAt\nT1,,9.999,\n"));
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(BizException.class, () -> parser.parse(" \n"));
    }

    private String readSample() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/reconcile/sample-bill.csv")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
