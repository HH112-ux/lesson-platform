package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import com.lp.trade.reconcile.ReconcileBillCsvParser;
import com.lp.trade.reconcile.ReconcileCalculator;
import com.lp.trade.reconcile.ReconcilePageView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ReconcileService}. */
@ExtendWith(MockitoExtension.class)
class ReconcileServiceTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 10);

    @Mock private PaymentReconcileMapper reconcileMapper;

    @Mock private PaymentSettleBatchMapper settleBatchMapper;

    private ReconcileService service;

    @BeforeEach
    void setUp() {
        service =
                new ReconcileService(
                        reconcileMapper,
                        settleBatchMapper,
                        new ReconcileBillCsvParser(),
                        new ReconcileCalculator(),
                        new ObjectMapper(),
                        new SimpleMeterRegistry());
    }

    @Test
    void rejectsImportWhenBillDayIsSettled() {
        when(settleBatchMapper.countClosed(1, BILL_DATE)).thenReturn(1L);

        BizException error =
                assertThrows(
                        BizException.class,
                        () -> service.importBill(1, BILL_DATE, 1, csv("T1,LP1,9.90,")));

        assertEquals(ErrorCode.STATE_CONFLICT.getCode(), error.getCode());
        verify(reconcileMapper, never()).upsert(any());
    }

    @Test
    void rejectsRecheckWhenBillDayIsSettled() {
        PaymentReconcile existing = new PaymentReconcile();
        existing.setChannel(1);
        existing.setBillDate(BILL_DATE);
        existing.setBillType(1);
        existing.setRawBill(csv("T1,LP1,9.90,"));
        when(reconcileMapper.selectById(7L)).thenReturn(existing);
        when(settleBatchMapper.countClosed(1, BILL_DATE)).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.recheck(7L));

        assertEquals(ErrorCode.STATE_CONFLICT.getCode(), error.getCode());
        verify(reconcileMapper, never()).upsert(any());
    }

    @Test
    void importsMatchedBillAsStatusOne() {
        when(reconcileMapper.selectLocalPayments(eq(1), any(), any()))
                .thenReturn(List.of(payment(11L, "T1", "9.90")));
        when(reconcileMapper.selectByKey(any(), any(), any())).thenReturn(new PaymentReconcile());

        service.importBill(1, BILL_DATE, 1, csv("T1,LP1,9.90,"));

        PaymentReconcile saved = captureUpsert();
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals(Integer.valueOf(0), saved.getDiffCount());
        assertEquals(new BigDecimal("9.90"), saved.getChannelAmount());
        assertEquals(new BigDecimal("9.90"), saved.getLocalAmount());
        verify(reconcileMapper, never()).selectLocalRefunds(any(), any(), any());
    }

    @Test
    void importsDifferencesAsStatusTwo() {
        when(reconcileMapper.selectLocalPayments(eq(1), any(), any()))
                .thenReturn(List.of(payment(11L, "T1", "8.90")));
        when(reconcileMapper.selectByKey(any(), any(), any())).thenReturn(new PaymentReconcile());

        service.importBill(1, BILL_DATE, 1, csv("T1,LP1,9.90,", "T2,LP2,5.00,"));

        PaymentReconcile saved = captureUpsert();
        assertEquals(Integer.valueOf(2), saved.getStatus());
        assertEquals(Integer.valueOf(2), saved.getDiffCount());
        assertEquals(new BigDecimal("6.00"), saved.getDiffAmount());
    }

    @Test
    void importsRefundBillAgainstRefundRequests() {
        when(reconcileMapper.selectLocalRefunds(eq(1), any(), any()))
                .thenReturn(List.of(refund(21L, "R1", "9.90")));
        when(reconcileMapper.selectByKey(any(), any(), any())).thenReturn(new PaymentReconcile());

        service.importBill(1, BILL_DATE, 2, csv("R1,LP-R1,9.90,"));

        PaymentReconcile saved = captureUpsert();
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals(Integer.valueOf(2), saved.getBillType());
        verify(reconcileMapper, never()).selectLocalPayments(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedChannel() {
        assertThrows(
                BizException.class, () -> service.importBill(2, BILL_DATE, 1, csv("T1,,9.90,")));
        verify(reconcileMapper, never()).upsert(any());
    }

    @Test
    void dailyReturnsPagedAggregatesWithClampedPaging() {
        when(reconcileMapper.count(1, BILL_DATE, null)).thenReturn(1L);
        when(reconcileMapper.page(1, BILL_DATE, null, 0, 100))
                .thenReturn(List.of(aggregate(7L, 1)));

        ReconcilePageView page = service.daily(1, BILL_DATE, null, 0, 500);

        assertEquals(1, page.records().size());
        assertEquals(Long.valueOf(7L), page.records().get(0).id());
        assertEquals(1L, page.total());
        assertEquals(1, page.pageNum());
        assertEquals(100, page.pageSize());
    }

    @Test
    void dailyRejectsMissingBillDate() {
        assertThrows(BizException.class, () -> service.daily(1, null, null, 1, 20));
        verify(reconcileMapper, never()).count(any(), any(), any());
    }

    @Test
    void dailyRejectsUnknownStatus() {
        assertThrows(BizException.class, () -> service.daily(1, BILL_DATE, 4, 1, 20));
        verify(reconcileMapper, never()).count(any(), any(), any());
    }

    @Test
    void recheckPicksUpLatePaymentAndClearsDifferences() {
        PaymentReconcile stored = aggregate(7L, 2);
        stored.setRawBill(csv("T1,LP1,9.90,"));
        when(reconcileMapper.selectById(7L)).thenReturn(stored);
        when(reconcileMapper.selectLocalPayments(eq(1), any(), any()))
                .thenReturn(List.of(payment(11L, "T1", "9.90")));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1)).thenReturn(aggregate(7L, 1));

        PaymentReconcile result = service.recheck(7L);

        assertEquals(Integer.valueOf(1), result.getStatus());
        assertEquals(Integer.valueOf(1), captureUpsert().getStatus());
    }

    @Test
    void recheckFailsWithUnresolvedCodeWhenDifferencesRemain() {
        PaymentReconcile stored = aggregate(7L, 2);
        stored.setRawBill(csv("T1,LP1,9.90,", "T2,LP2,5.00,"));
        when(reconcileMapper.selectById(7L)).thenReturn(stored);
        when(reconcileMapper.selectLocalPayments(eq(1), any(), any())).thenReturn(List.of());
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1)).thenReturn(aggregate(7L, 2));

        BizException ex = assertThrows(BizException.class, () -> service.recheck(7L));

        assertEquals(50204, ex.getCode());
        assertEquals(Integer.valueOf(2), captureUpsert().getStatus());
    }

    @Test
    void recheckRejectsMissingAggregate() {
        when(reconcileMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.recheck(99L));

        assertEquals(40401, ex.getCode());
        verify(reconcileMapper, never()).upsert(any());
    }

    @Test
    void handleMarksPendingAggregateAsHandled() {
        when(reconcileMapper.selectById(7L))
                .thenReturn(aggregate(7L, 2))
                .thenReturn(aggregate(7L, 3));
        when(reconcileMapper.markHandled(eq(7L), eq(42L), any())).thenReturn(1);

        PaymentReconcile handled = service.handle(7L, 42L);

        assertEquals(Integer.valueOf(3), handled.getStatus());
    }

    @Test
    void handleRejectsAggregateThatIsNotPending() {
        when(reconcileMapper.selectById(7L)).thenReturn(aggregate(7L, 1));

        BizException ex = assertThrows(BizException.class, () -> service.handle(7L, 42L));

        assertEquals(40901, ex.getCode());
        verify(reconcileMapper, never()).markHandled(any(), any(), any());
    }

    private PaymentReconcile aggregate(Long id, int status) {
        PaymentReconcile row = new PaymentReconcile();
        row.setId(id);
        row.setChannel(1);
        row.setBillDate(BILL_DATE);
        row.setBillType(1);
        row.setChannelCount(1);
        row.setChannelAmount(new BigDecimal("9.90"));
        row.setLocalCount(1);
        row.setLocalAmount(new BigDecimal("9.90"));
        row.setDiffCount(0);
        row.setDiffAmount(BigDecimal.ZERO);
        row.setStatus(status);
        return row;
    }

    private PaymentReconcile captureUpsert() {
        ArgumentCaptor<PaymentReconcile> captor = ArgumentCaptor.forClass(PaymentReconcile.class);
        verify(reconcileMapper).upsert(captor.capture());
        return captor.getValue();
    }

    private String csv(String... rows) {
        return "tradeNo,merchantOrderNo,amount,occurredAt\n" + String.join("\n", rows) + "\n";
    }

    private PaymentRecord payment(Long id, String tradeNo, String amount) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(id);
        payment.setChannelTradeNo(tradeNo);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidAt(LocalDateTime.of(2026, 11, 10, 10, 0));
        return payment;
    }

    private RefundRequest refund(Long id, String tradeNo, String amount) {
        RefundRequest refund = new RefundRequest();
        refund.setId(id);
        refund.setChannelRefundNo(tradeNo);
        refund.setAmount(new BigDecimal(amount));
        refund.setRefundedAt(LocalDateTime.of(2026, 11, 10, 10, 0));
        return refund;
    }
}
