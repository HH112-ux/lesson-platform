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
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.reconcile.ReconcileBillCsvParser;
import com.lp.trade.reconcile.ReconcileCalculator;
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

    private ReconcileService service;

    @BeforeEach
    void setUp() {
        service =
                new ReconcileService(
                        reconcileMapper,
                        new ReconcileBillCsvParser(),
                        new ReconcileCalculator(),
                        new ObjectMapper(),
                        new SimpleMeterRegistry());
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
