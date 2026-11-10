package com.lp.trade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.reconcile.LocalReconcileRecord;
import com.lp.trade.reconcile.ReconcileBillCsvParser;
import com.lp.trade.reconcile.ReconcileBillLine;
import com.lp.trade.reconcile.ReconcileCalculator;
import com.lp.trade.reconcile.ReconcileResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a channel bill CSV and stores the daily reconciliation aggregate.
 *
 * <p>Only Alipay is reconciled in the first release. The import is idempotent on {@code channel,
 * bill_date, bill_type}: re-importing the same day overwrites the aggregate and recomputes the
 * difference list, so duplicated files never create duplicate differences. Differences are recorded
 * and alerted; this service never changes order, payment, refund or user fund state.
 *
 * @author Caspian
 * @since 0.6.1
 */
@Service
public class ReconcileService {

    static final int BILL_TYPE_PAYMENT = 1;

    static final int BILL_TYPE_REFUND = 2;

    private static final Logger LOG = LoggerFactory.getLogger(ReconcileService.class);

    private final PaymentReconcileMapper reconcileMapper;

    private final ReconcileBillCsvParser csvParser;

    private final ReconcileCalculator calculator;

    private final ObjectMapper objectMapper;

    private final MeterRegistry meterRegistry;

    /**
     * Creates the reconciliation service.
     *
     * @param reconcileMapper persistence for {@code payment_reconcile} and the local trade rows
     * @param csvParser parses the uploaded UTF-8 bill CSV
     * @param calculator compares the channel bill with the local rows
     * @param objectMapper serializes the difference list
     * @param meterRegistry registers the difference alert counter
     * @author Caspian
     * @since 0.6.1
     */
    public ReconcileService(
            PaymentReconcileMapper reconcileMapper,
            ReconcileBillCsvParser csvParser,
            ReconcileCalculator calculator,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.reconcileMapper = reconcileMapper;
        this.csvParser = csvParser;
        this.calculator = calculator;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Imports one bill day and bill type, stores the aggregate and returns the persisted row.
     *
     * @param channel payment channel; only {@link PaymentChannel#ALIPAY} is supported
     * @param billDate bill day in Asia/Shanghai
     * @param billType 1 payment bill, 2 refund bill
     * @param csvText raw UTF-8 CSV content
     * @return persisted aggregate, including the primary key
     * @throws BizException when the channel, bill type or CSV content is invalid
     * @author Caspian
     * @since 0.6.1
     */
    @Transactional
    public PaymentReconcile importBill(
            Integer channel, LocalDate billDate, Integer billType, String csvText) {
        validateRequest(channel, billDate, billType);
        List<ReconcileBillLine> channelLines = csvParser.parse(csvText);
        LocalDateTime start = billDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        int localRefType;
        List<LocalReconcileRecord> localLines;
        if (billType == BILL_TYPE_PAYMENT) {
            localRefType = 1;
            localLines =
                    reconcileMapper.selectLocalPayments(channel, start, end).stream()
                            .map(this::toLocal)
                            .toList();
        } else {
            localRefType = 2;
            localLines =
                    reconcileMapper.selectLocalRefunds(channel, start, end).stream()
                            .map(this::toLocal)
                            .toList();
        }
        ReconcileResult result = calculator.calculate(channelLines, localLines, localRefType);
        String diffJson = serializeDiffs(result);
        PaymentReconcile existing = reconcileMapper.selectByKey(channel, billDate, billType);
        PaymentReconcile row = buildRow(channel, billDate, billType, csvText, diffJson, result);
        applyStatus(row, existing, result, diffJson);
        reconcileMapper.upsert(row);
        if (result.diffCount() > 0) {
            LOG.error(
                    "reconcile differences detected channel={} billDate={} billType={} "
                            + "diffCount={} diffAmount={}",
                    channel,
                    billDate,
                    billType,
                    result.diffCount(),
                    result.diffAmount());
            meterRegistry
                    .counter("reconcile_diff_total", "channel", String.valueOf(channel))
                    .increment(result.diffCount());
        }
        return reconcileMapper.selectByKey(channel, billDate, billType);
    }

    private void validateRequest(Integer channel, LocalDate billDate, Integer billType) {
        if (!Objects.equals(channel, PaymentChannel.ALIPAY)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "only alipay reconciliation is supported in the first release");
        }
        if (billDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "billDate is required");
        }
        if (!Objects.equals(billType, BILL_TYPE_PAYMENT)
                && !Objects.equals(billType, BILL_TYPE_REFUND)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "billType must be 1 or 2");
        }
    }

    private LocalReconcileRecord toLocal(PaymentRecord payment) {
        return new LocalReconcileRecord(
                payment.getId(), payment.getChannelTradeNo(), payment.getAmount());
    }

    private LocalReconcileRecord toLocal(RefundRequest refund) {
        return new LocalReconcileRecord(
                refund.getId(), refund.getChannelRefundNo(), refund.getAmount());
    }

    private PaymentReconcile buildRow(
            Integer channel,
            LocalDate billDate,
            Integer billType,
            String csvText,
            String diffJson,
            ReconcileResult result) {
        PaymentReconcile row = new PaymentReconcile();
        row.setChannel(channel);
        row.setBillDate(billDate);
        row.setBillType(billType);
        row.setChannelCount(result.channelCount());
        row.setChannelAmount(result.channelAmount());
        row.setLocalCount(result.localCount());
        row.setLocalAmount(result.localAmount());
        row.setDiffCount(result.diffCount());
        row.setDiffAmount(result.diffAmount());
        row.setRawBill(csvText);
        row.setDiffDetail(diffJson);
        return row;
    }

    private void applyStatus(
            PaymentReconcile row,
            PaymentReconcile existing,
            ReconcileResult result,
            String diffJson) {
        if (result.diffCount() == 0) {
            row.setStatus(1);
            return;
        }
        boolean unchangedHandled =
                existing != null
                        && Objects.equals(existing.getStatus(), 3)
                        && Objects.equals(existing.getDiffDetail(), diffJson);
        if (unchangedHandled) {
            row.setStatus(3);
            row.setHandledBy(existing.getHandledBy());
            row.setHandledAt(existing.getHandledAt());
            return;
        }
        row.setStatus(2);
    }

    private String serializeDiffs(ReconcileResult result) {
        try {
            return objectMapper.writeValueAsString(result.diffs());
        } catch (JsonProcessingException ex) {
            throw new BizException(
                    ErrorCode.INTERNAL_ERROR, "failed to serialize reconcile differences");
        }
    }
}
