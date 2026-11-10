package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link SettlementService}. */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 9);

    private static final long CLOSED_BY = 9001L;

    @Mock private PaymentReconcileMapper reconcileMapper;

    @Mock private PaymentSettleBatchMapper settleBatchMapper;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(reconcileMapper, settleBatchMapper);
    }

    @Test
    void closesDayAndRecordsNetAmounts() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(3, "9.90", "9.90", 2, "1.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "3.00", "2.00", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(0L);
        when(settleBatchMapper.selectByKey(1, BILL_DATE)).thenReturn(null, closed(77L));

        PaymentSettleBatch saved = service.close(1, BILL_DATE, CLOSED_BY);

        ArgumentCaptor<PaymentSettleBatch> captor =
                ArgumentCaptor.forClass(PaymentSettleBatch.class);
        verify(settleBatchMapper).upsert(captor.capture());
        PaymentSettleBatch written = captor.getValue();
        assertEquals(new BigDecimal("9.90"), written.getChannelPayAmount());
        assertEquals(new BigDecimal("3.00"), written.getChannelRefundAmount());
        assertEquals(new BigDecimal("2.00"), written.getLocalRefundAmount());
        assertEquals(new BigDecimal("6.90"), written.getNetChannelAmount());
        assertEquals(new BigDecimal("7.90"), written.getNetLocalAmount());
        assertEquals(Integer.valueOf(2), written.getDiffCount());
        assertEquals(new BigDecimal("1.00"), written.getDiffAmount());
        assertEquals(Integer.valueOf(SettlementService.STATUS_CLOSED), written.getStatus());
        assertEquals(Long.valueOf(CLOSED_BY), written.getClosedBy());
        assertEquals(Long.valueOf(77L), saved.getId());
    }

    @Test
    void allowsNegativeNetWhenRefundsExceedPayments() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(1, "0.00", "0.00", 0, "0.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "30.01", "30.01", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(0L);
        when(settleBatchMapper.selectByKey(1, BILL_DATE)).thenReturn(null, closed(1L));

        service.close(1, BILL_DATE, CLOSED_BY);

        ArgumentCaptor<PaymentSettleBatch> captor =
                ArgumentCaptor.forClass(PaymentSettleBatch.class);
        verify(settleBatchMapper).upsert(captor.capture());
        assertEquals(new BigDecimal("-30.01"), captor.getValue().getNetChannelAmount());
        assertEquals(new BigDecimal("-30.01"), captor.getValue().getNetLocalAmount());
    }

    @Test
    void keepsCentPrecisionWhenNettingAmounts() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(1, "100.00", "100.00", 0, "0.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "30.01", "30.01", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(0L);
        when(settleBatchMapper.selectByKey(1, BILL_DATE)).thenReturn(null, closed(1L));

        service.close(1, BILL_DATE, CLOSED_BY);

        ArgumentCaptor<PaymentSettleBatch> captor =
                ArgumentCaptor.forClass(PaymentSettleBatch.class);
        verify(settleBatchMapper).upsert(captor.capture());
        assertEquals(new BigDecimal("69.99"), captor.getValue().getNetChannelAmount());
    }

    @Test
    void rejectsDayWithoutPaymentBill() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1)).thenReturn(null);

        BizException error =
                assertThrows(BizException.class, () -> service.close(1, BILL_DATE, CLOSED_BY));

        assertEquals(ErrorCode.SETTLE_BILL_INCOMPLETE.getCode(), error.getCode());
        verify(settleBatchMapper, never()).upsert(any());
    }

    @Test
    void rejectsDayWithoutRefundBill() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(1, "9.90", "9.90", 0, "0.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2)).thenReturn(null);

        BizException error =
                assertThrows(BizException.class, () -> service.close(1, BILL_DATE, CLOSED_BY));

        assertEquals(ErrorCode.SETTLE_BILL_INCOMPLETE.getCode(), error.getCode());
        verify(settleBatchMapper, never()).upsert(any());
    }

    @Test
    void rejectsPendingDifferencesWithoutWriting() {
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(2, "9.90", "9.90", 1, "1.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "0.00", "0.00", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(1L);

        BizException error =
                assertThrows(BizException.class, () -> service.close(1, BILL_DATE, CLOSED_BY));

        assertEquals(ErrorCode.RECONCILE_UNRESOLVED.getCode(), error.getCode());
        verify(settleBatchMapper, never()).upsert(any());
        verify(settleBatchMapper, never()).selectByKey(any(), any());
    }

    @Test
    void returnsStoredBatchWhenClosedAgain() {
        PaymentSettleBatch existing = closed(77L);
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(1, "9.90", "9.90", 0, "0.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "0.00", "0.00", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(0L);
        when(settleBatchMapper.selectByKey(1, BILL_DATE)).thenReturn(existing);

        assertSame(existing, service.close(1, BILL_DATE, CLOSED_BY));
        verify(settleBatchMapper, never()).upsert(any());
    }

    @Test
    void reclosesVoidedDay() {
        PaymentSettleBatch voided = closed(77L);
        voided.setStatus(2);
        when(reconcileMapper.selectByKey(1, BILL_DATE, 1))
                .thenReturn(row(1, "9.90", "9.90", 0, "0.00"));
        when(reconcileMapper.selectByKey(1, BILL_DATE, 2))
                .thenReturn(row(1, "0.00", "0.00", 0, "0.00"));
        when(reconcileMapper.countUnresolved(1, BILL_DATE)).thenReturn(0L);
        when(settleBatchMapper.selectByKey(1, BILL_DATE)).thenReturn(voided, closed(77L));

        service.close(1, BILL_DATE, CLOSED_BY);

        verify(settleBatchMapper).upsert(any());
    }

    @Test
    void rejectsChannelOtherThanAlipay() {
        BizException error =
                assertThrows(BizException.class, () -> service.close(2, BILL_DATE, CLOSED_BY));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), error.getCode());
        verifyNoInteractions(reconcileMapper, settleBatchMapper);
    }

    private PaymentReconcile row(
            int status,
            String channelAmount,
            String localAmount,
            int diffCount,
            String diffAmount) {
        PaymentReconcile row = new PaymentReconcile();
        row.setStatus(status);
        row.setChannelCount(1);
        row.setChannelAmount(new BigDecimal(channelAmount));
        row.setLocalCount(1);
        row.setLocalAmount(new BigDecimal(localAmount));
        row.setDiffCount(diffCount);
        row.setDiffAmount(new BigDecimal(diffAmount));
        return row;
    }

    private PaymentSettleBatch closed(long id) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setId(id);
        batch.setStatus(1);
        return batch;
    }
}
