package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.mapper.CouponMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.service.PaymentChannelSyncService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the timeout close against the branch where a payment callback wins the race.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutJobTest {

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private CouponMapper couponMapper;

    @Mock private TransactionTemplate transactionTemplate;

    @Mock private PaymentChannelSyncService paymentChannelSync;

    private OrderTimeoutJob job;

    /**
     * Wires the job and makes the transaction template run its callback inline.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void setUp() {
        job =
                new OrderTimeoutJob(
                        tradeOrderMapper,
                        paymentMapper,
                        couponMapper,
                        transactionTemplate,
                        paymentChannelSync);
        when(transactionTemplate.execute(any()))
                .thenAnswer(
                        invocation -> {
                            TransactionCallback<?> callback = invocation.getArgument(0);
                            return callback.doInTransaction(mock(TransactionStatus.class));
                        });
    }

    /**
     * A close that wins releases the pending payment and the occupied coupon.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void closesOrderWithPendingPaymentAndCoupon() {
        TradeOrder order = new TradeOrder();
        order.setId(7L);
        when(tradeOrderMapper.selectExpiredPending(any(), eq(200))).thenReturn(List.of(order));
        when(paymentChannelSync.prepareClose(order)).thenReturn(true);
        when(tradeOrderMapper.markTimedOut(eq(7L), any())).thenReturn(1);

        job.scan();

        verify(paymentMapper).closePendingByOrderId(7L);
        verify(couponMapper).releaseByOrderId(7L);
    }

    /**
     * A callback that already committed leaves the order paid and every dependent row untouched.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void leavesPaidOrderUntouched() {
        when(tradeOrderMapper.markTimedOut(eq(7L), any())).thenReturn(0);

        assertFalse(job.closeOne(7L, LocalDateTime.now()));

        verifyNoInteractions(paymentMapper, couponMapper);
    }

    /**
     * A repeated scan over an already closed order neither fails nor writes again.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void repeatedCloseStaysIdempotent() {
        when(tradeOrderMapper.markTimedOut(eq(7L), any())).thenReturn(0);

        assertDoesNotThrow(() -> job.closeOne(7L, LocalDateTime.now()));
        assertDoesNotThrow(() -> job.closeOne(7L, LocalDateTime.now()));

        verify(paymentMapper, never()).closePendingByOrderId(anyLong());
        verify(couponMapper, never()).releaseByOrderId(anyLong());
    }

    /**
     * One broken order does not stop the remaining orders in the batch.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void continuesAfterOneOrderFails() {
        TradeOrder first = new TradeOrder();
        first.setId(1L);
        TradeOrder second = new TradeOrder();
        second.setId(2L);
        when(tradeOrderMapper.selectExpiredPending(any(), eq(200)))
                .thenReturn(List.of(first, second));
        when(paymentChannelSync.prepareClose(first)).thenReturn(true);
        when(paymentChannelSync.prepareClose(second)).thenReturn(true);
        when(tradeOrderMapper.markTimedOut(eq(1L), any()))
                .thenThrow(new IllegalStateException("lock wait timeout"));
        when(tradeOrderMapper.markTimedOut(eq(2L), any())).thenReturn(1);

        job.scan();

        verify(tradeOrderMapper).markTimedOut(eq(2L), any());
        verify(paymentMapper).closePendingByOrderId(2L);
        verify(paymentMapper, never()).closePendingByOrderId(1L);
    }

    /**
     * A channel close the platform cannot confirm is isolated to that one order.
     *
     * <p>The failing order stays pending for the next scan, and the rest of the batch still closes;
     * one unconfirmed channel answer never aborts the tick.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void keepsClosingBatchWhenOneChannelCloseFails() {
        TradeOrder failing = new TradeOrder();
        failing.setId(1L);
        TradeOrder healthy = new TradeOrder();
        healthy.setId(2L);
        when(tradeOrderMapper.selectExpiredPending(any(), eq(200)))
                .thenReturn(List.of(failing, healthy));
        when(paymentChannelSync.prepareClose(failing))
                .thenThrow(new BizException(ErrorCode.PAYMENT_CHANNEL_CLOSE_FAILED));
        when(paymentChannelSync.prepareClose(healthy)).thenReturn(true);
        when(tradeOrderMapper.markTimedOut(eq(2L), any())).thenReturn(1);

        job.scan();

        verify(tradeOrderMapper, never()).markTimedOut(eq(1L), any());
        verify(tradeOrderMapper).markTimedOut(eq(2L), any());
        verify(paymentMapper).closePendingByOrderId(2L);
    }
}
