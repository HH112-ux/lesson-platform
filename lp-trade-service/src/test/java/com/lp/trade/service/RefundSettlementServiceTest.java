package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for the shared settlement gate; persistence and the revoke port are mocked.
 *
 * <p>The caller is not trusted: every test drives the gate through the mapper results, so a broken
 * job or a duplicated review can only ever produce {@code 0} rather than a second settlement.
 *
 * @author Caspian
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
class RefundSettlementServiceTest {

    private static final long USER_ID = 1001L;

    private static final long ORDER_ID = 8L;

    private static final long COURSE_ID = 7L;

    private static final long REFUND_ID = 21L;

    private static final String ORDER_NO = "LP20260101000000001";

    private static final String CHANNEL_REFUND_NO = "MOCK-RF" + ORDER_NO;

    private static final String SETTLED_BY = "review:9001";

    private static final BigDecimal AMOUNT = new BigDecimal("9.90");

    private static final long AMOUNT_FEN = 990L;

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private RefundRequestMapper refundRequestMapper;

    @Mock private EntitlementService entitlementService;

    private RefundSettlementService settlementService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @BeforeEach
    void setUp() {
        settlementService =
                new RefundSettlementService(
                        tradeOrderMapper,
                        paymentMapper,
                        refundRequestMapper,
                        entitlementService,
                        new TransactionTemplate(new SynchronousTransactionManager()));
    }

    /**
     * A channel-done refund moves to refunded and revokes access in the same call.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void settlesChannelDoneRefundAndRevokesEntitlement() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refund(RefundStatus.APPROVED));
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(refundingOrder());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markSuccess(
                        eq(REFUND_ID), eq(CHANNEL_REFUND_NO), eq(AMOUNT), eq(SETTLED_BY), any()))
                .thenReturn(1);
        when(tradeOrderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(paymentMapper.markRefunded(3L)).thenReturn(1);

        int settled =
                settlementService.settle(REFUND_ID, AMOUNT_FEN, CHANNEL_REFUND_NO, SETTLED_BY);

        assertEquals(1, settled);
        verify(paymentMapper).markRefunded(3L);
        verify(entitlementService).revoke(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
    }

    /**
     * Without a channel-done finding the call writes nothing, even when the caller asks for it.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void skipsWhenFindingIsNotChannelDone() {
        RefundRequest refund = refund(RefundStatus.UNKNOWN);
        refund.setReconcileFinding(RefundReconcileFinding.CHANNEL_OTHER);
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refund);

        int settled = settlementService.settle(REFUND_ID, AMOUNT_FEN, null, "job");

        assertEquals(0, settled);
        verifyNoInteractions(tradeOrderMapper, paymentMapper, entitlementService);
    }

    /**
     * A channel amount that differs from the stored amount is rejected before any write.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void skipsWhenAmountDiffers() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refund(RefundStatus.APPROVED));

        int settled = settlementService.settle(REFUND_ID, AMOUNT_FEN + 1, null, "job");

        assertEquals(0, settled);
        verifyNoInteractions(tradeOrderMapper, paymentMapper, entitlementService);
    }

    /**
     * A second call after settlement is a silent no-op, never a state error.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void repeatedCallIsNoOpWithoutException() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refund(RefundStatus.REFUNDED));

        assertEquals(0, settlementService.settle(REFUND_ID, AMOUNT_FEN, null, "job"));
        assertEquals(0, settlementService.settle(REFUND_ID, AMOUNT_FEN, null, "job"));
        verifyNoInteractions(tradeOrderMapper, paymentMapper, entitlementService);
    }

    /**
     * Losing the conditional update race stops before the order, payment and revoke writes.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void skipsWhenConditionalUpdateLosesRace() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refund(RefundStatus.APPROVED));
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(refundingOrder());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markSuccess(
                        eq(REFUND_ID), eq(CHANNEL_REFUND_NO), eq(AMOUNT), eq(SETTLED_BY), any()))
                .thenReturn(0);

        int settled =
                settlementService.settle(REFUND_ID, AMOUNT_FEN, CHANNEL_REFUND_NO, SETTLED_BY);

        assertEquals(0, settled);
        verify(tradeOrderMapper, never()).markRefunded(eq(ORDER_ID), any());
        verify(paymentMapper, never()).markRefunded(anyLong());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), any());
    }

    private RefundRequest refund(int status) {
        RefundRequest refund = new RefundRequest();
        refund.setId(REFUND_ID);
        refund.setRefundNo("RF" + ORDER_NO);
        refund.setOrderId(ORDER_ID);
        refund.setUserId(USER_ID);
        refund.setAmount(AMOUNT);
        refund.setChannel(1);
        refund.setStatus(status);
        refund.setReconcileFinding(RefundReconcileFinding.CHANNEL_DONE);
        return refund;
    }

    private TradeOrder refundingOrder() {
        TradeOrder order = new TradeOrder();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.REFUNDING);
        return order;
    }

    private PaymentRecord payment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(3L);
        payment.setOrderId(ORDER_ID);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(AMOUNT);
        payment.setChannel(1);
        return payment;
    }

    private TradeOrderItem item() {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(ORDER_ID);
        item.setCourseId(COURSE_ID);
        return item;
    }

    /**
     * Runs every callback synchronously so the transaction boundary stays observable.
     *
     * @author Caspian
     * @since 0.8.0
     */
    private static final class SynchronousTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
