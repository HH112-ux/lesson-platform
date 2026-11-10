package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for voiding a settle batch in {@link SettlementService}. */
@ExtendWith(MockitoExtension.class)
class SettlementVoidTest {

    private static final long BATCH_ID = 77L;

    private static final long VOID_BY = 9002L;

    @Mock private PaymentReconcileMapper reconcileMapper;

    @Mock private PaymentSettleBatchMapper settleBatchMapper;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(reconcileMapper, settleBatchMapper);
    }

    @Test
    void voidsClosedBatchAndReturnsVoidedRow() {
        PaymentSettleBatch closed = batch(1);
        PaymentSettleBatch voided = batch(2);
        when(settleBatchMapper.selectById(BATCH_ID)).thenReturn(closed, voided);
        when(settleBatchMapper.voidBatch(eq(BATCH_ID), eq(VOID_BY), any())).thenReturn(1);

        PaymentSettleBatch result = service.voidSettleBatch(BATCH_ID, VOID_BY);

        assertEquals(Integer.valueOf(2), result.getStatus());
        verify(settleBatchMapper).voidBatch(eq(BATCH_ID), eq(VOID_BY), any());
    }

    @Test
    void rejectsMissingBatch() {
        when(settleBatchMapper.selectById(BATCH_ID)).thenReturn(null);

        BizException error =
                assertThrows(BizException.class, () -> service.voidSettleBatch(BATCH_ID, VOID_BY));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), error.getCode());
        verify(settleBatchMapper, never()).voidBatch(any(), any(), any());
    }

    @Test
    void rejectsBatchThatIsNotClosed() {
        when(settleBatchMapper.selectById(BATCH_ID)).thenReturn(batch(2));

        BizException error =
                assertThrows(BizException.class, () -> service.voidSettleBatch(BATCH_ID, VOID_BY));

        assertEquals(ErrorCode.STATE_CONFLICT.getCode(), error.getCode());
        verify(settleBatchMapper, never()).voidBatch(any(), any(), any());
    }

    @Test
    void rejectsWhenAnotherCallVoidedFirst() {
        when(settleBatchMapper.selectById(BATCH_ID)).thenReturn(batch(1));
        when(settleBatchMapper.voidBatch(eq(BATCH_ID), eq(VOID_BY), any())).thenReturn(0);

        BizException error =
                assertThrows(BizException.class, () -> service.voidSettleBatch(BATCH_ID, VOID_BY));

        assertEquals(ErrorCode.STATE_CONFLICT.getCode(), error.getCode());
    }

    private PaymentSettleBatch batch(int status) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setId(BATCH_ID);
        batch.setStatus(status);
        return batch;
    }
}
