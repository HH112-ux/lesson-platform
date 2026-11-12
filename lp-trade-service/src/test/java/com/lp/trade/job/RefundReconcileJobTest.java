package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.service.RefundSettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the reconciliation scan is read-only towards refund state, throttled and quiet.
 *
 * @author Caspian
 * @since 0.7.0
 */
class RefundReconcileJobTest {

    private final RefundRequestMapper refundRequestMapper = mock(RefundRequestMapper.class);

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);

    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);

    private final RefundReconcileMetrics metrics = mock(RefundReconcileMetrics.class);

    private final RefundSettlementService settlementService = mock(RefundSettlementService.class);

    @Test
    void reportsChannelSuccessWithoutChangingRefundState() {
        RefundRequest refund = refund(RefundStatus.APPROVED, null);
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of(refund));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(refund.getPaymentId())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, refund.getRefundNo()))
                .thenReturn(new ChannelRefundState("REFUND_SUCCESS", new BigDecimal("99.00")));

        assertDoesNotThrow(() -> job(true).scan());

        verify(paymentGateway).refundQuery(payment, refund.getRefundNo());
        verify(refundRequestMapper)
                .markReconcileChecked(
                        eq(1L), eq(RefundReconcileFinding.CHANNEL_DONE), any(LocalDateTime.class));
        verify(metrics).recordScanned();
        verify(metrics).refresh();
        verifyNoInteractions(settlementService);
    }

    @Test
    void convergesConfirmedRefundWhenTheSwitchIsOn() {
        RefundRequest refund = refund(RefundStatus.APPROVED, null);
        stubChannelSuccess(refund);
        when(settlementService.settle(1L, 9900L, null, "job")).thenReturn(1);

        assertDoesNotThrow(() -> job(true, true).scan());

        verify(settlementService).settle(1L, 9900L, null, "job");
        verify(metrics).recordConverged();
    }

    @Test
    void leavesConfirmedRefundAloneWhenTheSwitchIsOff() {
        RefundRequest refund = refund(RefundStatus.UNKNOWN, null);
        stubChannelSuccess(refund);

        assertDoesNotThrow(() -> job(true, false).scan());

        verifyNoInteractions(settlementService);
        verify(metrics, never()).recordConverged();
    }

    @Test
    void keepsScanningWhenConvergenceFails() {
        RefundRequest refund = refund(RefundStatus.APPROVED, null);
        stubChannelSuccess(refund);
        when(settlementService.settle(anyLong(), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> job(true, true).scan());

        verify(refundRequestMapper)
                .markReconcileChecked(
                        eq(1L), eq(RefundReconcileFinding.CHANNEL_DONE), any(LocalDateTime.class));
        verify(metrics, never()).recordConverged();
    }

    @Test
    void keepsUnknownRefundUntouchedWhenChannelHasNoRecord() {
        RefundRequest refund = refund(RefundStatus.UNKNOWN, RefundReconcileFinding.CHANNEL_OTHER);
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of(refund));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(refund.getPaymentId())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, refund.getRefundNo()))
                .thenReturn(ChannelRefundState.notFound());

        assertDoesNotThrow(() -> job(true).scan());

        verify(refundRequestMapper)
                .markReconcileChecked(
                        eq(1L),
                        eq(RefundReconcileFinding.CHANNEL_NOT_FOUND),
                        any(LocalDateTime.class));
    }

    @Test
    void skipsRefundsWhosePaymentIsNotSuccessful() {
        RefundRequest refund = refund(RefundStatus.APPROVED, null);
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of(refund));
        when(paymentMapper.selectById(refund.getPaymentId()))
                .thenReturn(payment(PaymentStatus.CLOSED));

        job(true).scan();

        verifyNoInteractions(paymentGateway);
        verify(refundRequestMapper)
                .markReconcileChecked(
                        eq(1L),
                        eq(RefundReconcileFinding.PAYMENT_UNSETTLED),
                        any(LocalDateTime.class));
        verify(metrics).refresh();
    }

    @Test
    void keepsScanningWhenOneQueryFails() {
        RefundRequest first = refund(RefundStatus.APPROVED, null);
        RefundRequest second = refund(RefundStatus.UNKNOWN, null);
        second.setId(2L);
        second.setRefundNo("RFORDER2");
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of(first, second));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(anyLong())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, first.getRefundNo()))
                .thenThrow(new IllegalArgumentException("channel down"));
        when(paymentGateway.refundQuery(payment, second.getRefundNo()))
                .thenReturn(ChannelRefundState.notFound());

        assertDoesNotThrow(() -> job(true).scan());

        verify(paymentGateway).refundQuery(payment, second.getRefundNo());
        verify(refundRequestMapper)
                .markReconcileChecked(
                        eq(1L), eq(RefundReconcileFinding.QUERY_FAILED), any(LocalDateTime.class));
    }

    @Test
    void appliesGracePeriodAndBackoffToTheScanWindow() {
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        job(true).scan();

        ArgumentCaptor<LocalDateTime> grace = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> firstRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> laterRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundRequestMapper)
                .selectReconcileCandidates(
                        grace.capture(), firstRetry.capture(), laterRetry.capture(), eq(50));
        assertTrue(!grace.getValue().isAfter(before.minusMinutes(10).plusSeconds(1)));
        assertTrue(!firstRetry.getValue().isAfter(before.minusHours(1).plusSeconds(1)));
        assertTrue(!laterRetry.getValue().isAfter(before.minusHours(6).plusSeconds(1)));
        verify(metrics).refresh();
    }

    @Test
    void doesNothingWhenDisabled() {
        job(false).scan();

        verifyNoInteractions(refundRequestMapper, paymentMapper, paymentGateway, metrics);
    }

    @Test
    void logsFindingWhenPreviousFindingDiffers() {
        RefundRequest refund = refund(RefundStatus.APPROVED, RefundReconcileFinding.CHANNEL_OTHER);
        stubChannelSuccess(refund);

        List<ILoggingEvent> events = scanAndCaptureLogs(true);

        assertEquals(1, events.size());
    }

    @Test
    void keepsQuietWhenFindingIsUnchanged() {
        RefundRequest refund = refund(RefundStatus.APPROVED, RefundReconcileFinding.CHANNEL_DONE);
        stubChannelSuccess(refund);

        List<ILoggingEvent> events = scanAndCaptureLogs(true);

        assertTrue(events.isEmpty());
    }

    private List<ILoggingEvent> scanAndCaptureLogs(boolean enabled) {
        Logger logger = (Logger) LoggerFactory.getLogger(RefundReconcileJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            job(enabled).scan();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private void stubChannelSuccess(RefundRequest refund) {
        when(refundRequestMapper.selectReconcileCandidates(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(50)))
                .thenReturn(List.of(refund));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(refund.getPaymentId())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, refund.getRefundNo()))
                .thenReturn(new ChannelRefundState("REFUND_SUCCESS", new BigDecimal("99.00")));
    }

    private RefundReconcileJob job(boolean enabled) {
        return job(enabled, false);
    }

    private RefundReconcileJob job(boolean enabled, boolean convergeEnabled) {
        return new RefundReconcileJob(
                refundRequestMapper,
                paymentMapper,
                paymentGateway,
                metrics,
                settlementService,
                enabled,
                convergeEnabled,
                10);
    }

    private RefundRequest refund(int status, Integer finding) {
        RefundRequest refund = new RefundRequest();
        refund.setId(1L);
        refund.setRefundNo("RFORDER1");
        refund.setOrderId(11L);
        refund.setPaymentId(21L);
        refund.setAmount(new BigDecimal("99.00"));
        refund.setStatus(status);
        refund.setReconcileFinding(finding);
        return refund;
    }

    private PaymentRecord payment(int status) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(21L);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("99.00"));
        return payment;
    }
}
