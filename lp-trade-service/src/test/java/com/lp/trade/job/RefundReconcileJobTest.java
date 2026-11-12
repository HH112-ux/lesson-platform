package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * Verifies that the reconciliation scan is read-only and uses the stored refund number.
 *
 * @author Caspian
 * @since 0.7.0
 */
class RefundReconcileJobTest {

    private final RefundRequestMapper refundRequestMapper = mock(RefundRequestMapper.class);

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);

    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);

    @Test
    void reportsChannelSuccessWithoutChangingAnything() {
        RefundRequest refund = refund(RefundStatus.APPROVED);
        when(refundRequestMapper.selectReconcileCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(refund));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(refund.getPaymentId())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, refund.getRefundNo()))
                .thenReturn(new ChannelRefundState("REFUND_SUCCESS", new BigDecimal("99.00")));

        assertDoesNotThrow(() -> job(10).scan());

        verify(paymentGateway).refundQuery(payment, refund.getRefundNo());
    }

    @Test
    void keepsUnknownRefundUntouchedWhenChannelHasNoRecord() {
        RefundRequest refund = refund(RefundStatus.UNKNOWN);
        when(refundRequestMapper.selectReconcileCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(refund));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(refund.getPaymentId())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, refund.getRefundNo()))
                .thenReturn(ChannelRefundState.notFound());

        assertDoesNotThrow(() -> job(10).scan());

        verify(paymentGateway).refundQuery(payment, refund.getRefundNo());
    }

    @Test
    void skipsRefundsWhosePaymentIsNotSuccessful() {
        RefundRequest refund = refund(RefundStatus.APPROVED);
        when(refundRequestMapper.selectReconcileCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(refund));
        when(paymentMapper.selectById(refund.getPaymentId()))
                .thenReturn(payment(PaymentStatus.CLOSED));

        job(10).scan();

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void keepsScanningWhenOneQueryFails() {
        RefundRequest first = refund(RefundStatus.APPROVED);
        RefundRequest second = refund(RefundStatus.UNKNOWN);
        second.setId(2L);
        second.setRefundNo("RFORDER2");
        when(refundRequestMapper.selectReconcileCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(first, second));
        PaymentRecord payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.selectById(anyLong())).thenReturn(payment);
        when(paymentGateway.refundQuery(payment, first.getRefundNo()))
                .thenThrow(new IllegalArgumentException("channel down"));
        when(paymentGateway.refundQuery(payment, second.getRefundNo()))
                .thenReturn(ChannelRefundState.notFound());

        assertDoesNotThrow(() -> job(10).scan());

        verify(paymentGateway).refundQuery(payment, second.getRefundNo());
    }

    @Test
    void appliesTheGracePeriodToTheScanWindow() {
        when(refundRequestMapper.selectReconcileCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusMinutes(10);

        job(10).scan();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundRequestMapper).selectReconcileCandidates(cutoff.capture(), eq(100));
        org.junit.jupiter.api.Assertions.assertTrue(
                !cutoff.getValue().isAfter(before.plusSeconds(1)));
        verify(paymentMapper, never()).selectById(anyLong());
    }

    private RefundReconcileJob job(long graceMinutes) {
        return new RefundReconcileJob(
                refundRequestMapper, paymentMapper, paymentGateway, graceMinutes);
    }

    private RefundRequest refund(int status) {
        RefundRequest refund = new RefundRequest();
        refund.setId(1L);
        refund.setRefundNo("RFORDER1");
        refund.setOrderId(11L);
        refund.setPaymentId(21L);
        refund.setAmount(new BigDecimal("99.00"));
        refund.setStatus(status);
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
