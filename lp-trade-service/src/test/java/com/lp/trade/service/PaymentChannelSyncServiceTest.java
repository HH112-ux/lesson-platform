package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.ChannelTradeState;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies the three convergence paths of the channel-first close.
 *
 * @author Caspian
 * @since 0.5.0
 */
@ExtendWith(MockitoExtension.class)
class PaymentChannelSyncServiceTest {

    @Mock private PaymentGateway paymentGateway;

    @Mock private PaymentService paymentService;

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private PaymentAnomalyRecorder anomalyRecorder;

    private PaymentChannelSyncService service;

    private TradeOrder order;

    private PaymentRecord payment;

    @BeforeEach
    void setUp() {
        service =
                new PaymentChannelSyncService(
                        paymentGateway,
                        paymentService,
                        tradeOrderMapper,
                        paymentMapper,
                        anomalyRecorder);
        order = new TradeOrder();
        order.setId(1L);
        order.setOrderNo("LP1");
        order.setStatus(OrderStatus.PENDING);
        payment = new PaymentRecord();
        payment.setId(9L);
        payment.setOrderId(1L);
        payment.setChannel(1);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentMapper.selectLatestByOrderId(1L)).thenReturn(payment);
    }

    @Test
    void closesLocallyWhenNoPendingAttemptExists() {
        when(paymentMapper.selectLatestByOrderId(1L)).thenReturn(null);

        assertTrue(service.prepareClose(order));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void closesLocallyWhenTheChannelConfirmsTheClose() {
        when(paymentGateway.close(payment)).thenReturn(true);
        when(tradeOrderMapper.selectById(1L)).thenReturn(order);

        assertTrue(service.prepareClose(order));
        verifyNoInteractions(paymentService, anomalyRecorder);
    }

    @Test
    void appliesTheChannelPaymentInsteadOfClosing() {
        when(paymentGateway.close(payment))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR));
        when(paymentGateway.query(payment)).thenReturn(paid("T1", new BigDecimal("99.00")));

        assertFalse(service.prepareClose(order));
        verify(paymentService)
                .applyConfirmedPayment(any(PaymentNotification.class), eq(order), eq(payment));
    }

    @Test
    void recordsAnAnomalyWhenTheChannelClosedButTheOrderIsPaid() {
        order.setStatus(OrderStatus.PAID);
        payment.setChannelTradeNo("T1");
        when(paymentGateway.close(payment)).thenReturn(true);
        when(tradeOrderMapper.selectById(1L)).thenReturn(order);
        when(paymentMapper.selectById(9L)).thenReturn(payment);

        assertFalse(service.prepareClose(order));
        verify(anomalyRecorder)
                .record(
                        any(PaymentNotification.class),
                        eq(new BigDecimal("99.00")),
                        eq(PaymentAnomalyRecorder.ORDER_NOT_PAYABLE),
                        anyString());
    }

    @Test
    void raisesCloseFailedWhenCloseAndQueryBothFail() {
        when(paymentGateway.close(payment))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR));
        when(paymentGateway.query(payment))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR));

        BizException exception =
                assertThrows(BizException.class, () -> service.prepareClose(order));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_CLOSE_FAILED.getCode(), exception.getCode());
    }

    @Test
    void raisesCloseFailedWhenTheTradeIsStillOpen() {
        when(paymentGateway.close(payment)).thenReturn(false);
        when(paymentGateway.query(payment))
                .thenReturn(new ChannelTradeState("WAIT_BUYER_PAY", null, null));

        BizException exception =
                assertThrows(BizException.class, () -> service.prepareClose(order));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_CLOSE_FAILED.getCode(), exception.getCode());
        verifyNoInteractions(paymentService);
    }

    @Test
    void treatsAnUnconfiguredChannelAsClosable() {
        when(paymentGateway.close(payment))
                .thenThrow(new BizException(ErrorCode.PAYMENT_NOT_CONFIGURED));

        assertTrue(service.prepareClose(order));
        verify(paymentGateway, never()).query(any());
    }

    @Test
    void doesNotApplyATradeThatIsAlreadyRecorded() {
        when(paymentGateway.close(payment))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR));
        when(paymentGateway.query(payment)).thenReturn(paid("T1", new BigDecimal("99.00")));
        when(paymentMapper.selectByChannelTradeNo(anyInt(), anyString()))
                .thenReturn(mock(PaymentRecord.class));

        assertFalse(service.prepareClose(order));
        verifyNoInteractions(paymentService);
    }

    private static ChannelTradeState paid(String tradeNo, BigDecimal amount) {
        return new ChannelTradeState(ChannelTradeState.TRADE_SUCCESS, tradeNo, amount);
    }
}
