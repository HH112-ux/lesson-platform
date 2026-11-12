package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that the reconciliation scan is read-only towards refund state and throttled.
 *
 * @author Caspian
 * @since 0.7.0
 */
class RefundReconcileJobTest {

    private final RefundRequestMapper refundRequestMapper = mock(RefundRequestMapper.class);

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);

    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);

    private final RefundReconcileMetrics metrics = mock(RefundReconcileMetrics.class);

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
                .markReconcileChecked(eq(1L), eq("CHANNEL_DONE"), any(LocalDateTime.class));
        verify(metrics).recordScanned();
        verify(metrics).refresh();
    }

    @Test
    void keepsUnknownRefundUntouchedWhenChannelHasNoRecord() {
        RefundRequest refund = refund(RefundStatus.UNKNOWN, "CHANNEL_OTHER");
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
                .markReconcileChecked(eq(1L), eq("CHANNEL_NOT_FOUND"), any(LocalDateTime.class));
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
                .markReconcileChecked(eq(1L), eq("PAYMENT_UNSETTLED"), any(LocalDateTime.class));
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
                .markReconcileChecked(eq(1L), eq("QUERY_FAILED"), any(LocalDateTime.class));
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
        org.junit.jupiter.api.Assertions.assertTrue(
                !grace.getValue().isAfter(before.minusMinutes(10).plusSeconds(1)));
        org.junit.jupiter.api.Assertions.assertTrue(
                !firstRetry.getValue().isAfter(before.minusHours(1).plusSeconds(1)));
        org.junit.jupiter.api.Assertions.assertTrue(
                !laterRetry.getValue().isAfter(before.minusHours(6).plusSeconds(1)));
        verify(metrics).refresh();
    }

    @Test
    void doesNothingWhenDisabled() {
        job(false).scan();

        verifyNoInteractions(refundRequestMapper, paymentMapper, paymentGateway, metrics);
    }

    private RefundReconcileJob job(boolean enabled) {
        return new RefundReconcileJob(
                refundRequestMapper, paymentMapper, paymentGateway, metrics, enabled, 10);
    }

    private RefundRequest refund(int status, String finding) {
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
