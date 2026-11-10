package com.lp.trade.reconcile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Compares an imported channel bill with the local payment or refund rows.
 *
 * <p>Matching is by channel trade number. A channel-only entry is a long difference (status 2), a
 * local-only entry is a short difference (status 3), and an equal trade number with a different
 * amount is an amount mismatch (status 4). The calculator only produces records; no fund status is
 * changed here.
 *
 * @author Caspian
 * @since 0.6.1
 */
@Component
public class ReconcileCalculator {

    /**
     * Calculates the aggregate and the difference list.
     *
     * @param channelLines channel bill lines for one bill day and bill type
     * @param localLines local payment or refund rows for the same day and type
     * @param localRefType 1 for {@code payment_record}, 2 for {@code refund_request}
     * @return aggregate and differences sorted by item key
     * @author Caspian
     * @since 0.6.1
     */
    public ReconcileResult calculate(
            List<ReconcileBillLine> channelLines,
            List<LocalReconcileRecord> localLines,
            int localRefType) {
        Map<String, LocalReconcileRecord> remaining = new LinkedHashMap<>();
        for (LocalReconcileRecord local : localLines) {
            if (local.tradeNo() != null && !local.tradeNo().isBlank()) {
                remaining.put(local.tradeNo(), local);
            }
        }
        List<ReconcileDiff> diffs = new ArrayList<>();
        BigDecimal channelAmount = BigDecimal.ZERO;
        for (ReconcileBillLine line : channelLines) {
            channelAmount = channelAmount.add(line.amount());
            LocalReconcileRecord local = remaining.remove(line.tradeNo());
            if (local == null) {
                diffs.add(
                        new ReconcileDiff(
                                "CHANNEL:" + line.tradeNo(),
                                line.tradeNo(),
                                localRefType,
                                null,
                                2,
                                line.amount(),
                                BigDecimal.ZERO.setScale(2),
                                line.amount()));
            } else if (line.amount().compareTo(local.amount()) != 0) {
                diffs.add(
                        new ReconcileDiff(
                                "CHANNEL:" + line.tradeNo(),
                                line.tradeNo(),
                                localRefType,
                                local.id(),
                                4,
                                line.amount(),
                                local.amount(),
                                line.amount().subtract(local.amount()).setScale(2)));
            }
        }
        for (LocalReconcileRecord local : remaining.values()) {
            diffs.add(
                    new ReconcileDiff(
                            "LOCAL:" + local.id(),
                            null,
                            localRefType,
                            local.id(),
                            3,
                            BigDecimal.ZERO.setScale(2),
                            local.amount(),
                            local.amount().negate().setScale(2)));
        }
        diffs.sort(Comparator.comparing(ReconcileDiff::itemKey));
        BigDecimal localAmount =
                localLines.stream()
                        .map(LocalReconcileRecord::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diffAmount =
                diffs.stream()
                        .map(ReconcileDiff::diffAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReconcileResult(
                channelLines.size(),
                channelAmount.setScale(2),
                localLines.size(),
                localAmount.setScale(2),
                diffs.size(),
                diffAmount.setScale(2),
                List.copyOf(diffs));
    }
}
