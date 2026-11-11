package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.mapper.AuditOutboxMapper;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import com.lp.trade.reconcile.SettlementPageView;
import com.lp.trade.reconcile.SettlementView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the settlement read model and queries of {@link SettlementService}. */
@ExtendWith(MockitoExtension.class)
class SettlementQueryTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 11, 9);

    @Mock private PaymentReconcileMapper reconcileMapper;

    @Mock private PaymentSettleBatchMapper settleBatchMapper;

    @Mock private AuditOutboxMapper auditOutboxMapper;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(reconcileMapper, settleBatchMapper, auditOutboxMapper);
    }

    @Test
    void readsMissingBatchAsNotFound() {
        when(settleBatchMapper.selectById(88L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.get(88L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void mapsAmountsToFenInPage() {
        when(settleBatchMapper.count(1, BILL_DATE, null)).thenReturn(1L);
        when(settleBatchMapper.page(1, BILL_DATE, null, 0, 20)).thenReturn(List.of(batch(77L)));

        SettlementPageView page = service.page(1, BILL_DATE, null, 1, 20);

        assertEquals(1, page.total());
        assertEquals(1, page.pageNum());
        assertEquals(20, page.pageSize());
        SettlementView view = page.records().get(0);
        assertEquals(7000L, view.netChannelAmountFen());
        assertEquals(3000L, view.channelRefundAmountFen());
        assertEquals(7000L, view.netLocalAmountFen());
        assertEquals(0L, view.diffAmountFen());
        assertEquals(Integer.valueOf(1), view.status());
    }

    @Test
    void capsPageSizeAtMaximum() {
        when(settleBatchMapper.count(1, null, 2)).thenReturn(0L);
        when(settleBatchMapper.page(1, null, 2, 0, SettlementService.MAX_PAGE_SIZE))
                .thenReturn(List.of());

        SettlementPageView page = service.page(1, null, 2, 1, 500);

        assertEquals(SettlementService.MAX_PAGE_SIZE, page.pageSize());
        verify(settleBatchMapper).page(1, null, 2, 0, SettlementService.MAX_PAGE_SIZE);
    }

    @Test
    void normalizesZeroPageNumToFirstPage() {
        when(settleBatchMapper.count(1, null, null)).thenReturn(0L);
        when(settleBatchMapper.page(1, null, null, 0, 20)).thenReturn(List.of());

        SettlementPageView page = service.page(1, null, null, 0, 20);

        assertEquals(1, page.pageNum());
        verify(settleBatchMapper).page(1, null, null, 0, 20);
    }

    @Test
    void normalizesNegativePageNumToFirstPage() {
        when(settleBatchMapper.count(1, null, null)).thenReturn(0L);
        when(settleBatchMapper.page(1, null, null, 0, 20)).thenReturn(List.of());

        SettlementPageView page = service.page(1, null, null, -7, 20);

        assertEquals(1, page.pageNum());
        verify(settleBatchMapper).page(1, null, null, 0, 20);
    }

    @Test
    void returnsEmptyPageWhenOffsetExceedsIntRange() {
        when(settleBatchMapper.count(1, null, null)).thenReturn(5L);

        SettlementPageView page = service.page(1, null, null, Integer.MAX_VALUE, 100);

        assertTrue(page.records().isEmpty());
        assertEquals(5, page.total());
        assertEquals(Integer.MAX_VALUE, page.pageNum());
        verify(settleBatchMapper, never()).page(anyInt(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void rejectsStatusOutsideSettledLifecycle() {
        BizException ex = assertThrows(BizException.class, () -> service.page(1, null, 0, 1, 20));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsChannelOtherThanAlipay() {
        BizException ex =
                assertThrows(BizException.class, () -> service.page(2, null, null, 1, 20));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }

    private PaymentSettleBatch batch(long id) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setId(id);
        batch.setChannel(1);
        batch.setBillDate(BILL_DATE);
        batch.setChannelPayCount(2);
        batch.setChannelPayAmount(new BigDecimal("100.00"));
        batch.setChannelRefundCount(1);
        batch.setChannelRefundAmount(new BigDecimal("30.00"));
        batch.setLocalPayCount(2);
        batch.setLocalPayAmount(new BigDecimal("100.00"));
        batch.setLocalRefundCount(1);
        batch.setLocalRefundAmount(new BigDecimal("30.00"));
        batch.setNetChannelAmount(new BigDecimal("70.00"));
        batch.setNetLocalAmount(new BigDecimal("70.00"));
        batch.setDiffCount(0);
        batch.setDiffAmount(new BigDecimal("0.00"));
        batch.setStatus(1);
        batch.setClosedBy(9001L);
        return batch;
    }
}
