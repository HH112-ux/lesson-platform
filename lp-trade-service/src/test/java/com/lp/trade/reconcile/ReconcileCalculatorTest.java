package com.lp.trade.reconcile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReconcileCalculator}. */
class ReconcileCalculatorTest {

    private final ReconcileCalculator calculator = new ReconcileCalculator();

    @Test
    void reportsMatchedWhenAmountsAgree() {
        ReconcileResult result =
                calculator.calculate(
                        List.of(line("T1", "9.90")), List.of(local(11L, "T1", "9.90")), 1);

        assertEquals(1, result.channelCount());
        assertEquals(0, result.diffCount());
        assertEquals(new BigDecimal("0.00"), result.diffAmount());
    }

    @Test
    void reportsLongShortAndAmountMismatch() {
        ReconcileResult result =
                calculator.calculate(
                        List.of(line("T1", "9.90"), line("T2", "20.00"), line("T3", "5.00")),
                        List.of(
                                local(11L, "T1", "8.90"),
                                local(12L, "T2", "20.00"),
                                local(13L, "T4", "3.00")),
                        1);

        assertEquals(3, result.diffCount());
        // T1: +1.00, T3: +5.00, T4 (local only): -3.00
        assertEquals(new BigDecimal("3.00"), result.diffAmount());
        assertEquals(4, result.diffs().get(0).matchStatus());
        assertEquals(2, result.diffs().get(1).matchStatus());
        assertEquals(3, result.diffs().get(2).matchStatus());
    }

    @Test
    void reportsShortDifferenceAsNegative() {
        ReconcileResult result =
                calculator.calculate(
                        List.of(line("T1", "9.90")),
                        List.of(local(11L, "T1", "9.90"), local(12L, "T2", "1.00")),
                        1);

        assertEquals(1, result.diffCount());
        assertEquals(new BigDecimal("-1.00"), result.diffAmount());
        assertEquals(3, result.diffs().get(0).matchStatus());
    }

    private ReconcileBillLine line(String tradeNo, String amount) {
        return new ReconcileBillLine(tradeNo, null, new BigDecimal(amount), null);
    }

    private LocalReconcileRecord local(Long id, String tradeNo, String amount) {
        return new LocalReconcileRecord(id, tradeNo, new BigDecimal(amount));
    }
}
