package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.RefundView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the refund loop; persistence and the payment channel are mocked.
 *
 * <p>The cases mirror the acceptance criteria of the refund drill: a paid order refunds once, a
 * repeated call replays the original refund without touching the channel, an amount above the paid
 * amount is rejected, and a successful refund satisfies the order and records the revocation.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long ORDER_ID = 8L;

    private static final long COURSE_ID = 7L;

    private static final long REFUND_ID = 21L;

    private static final String ORDER_NO = "LP20260101000000001";

    private static final String REFUND_NO = "RF" + ORDER_NO;

    private static final String CHANNEL_REFUND_NO = "MOCK-" + REFUND_NO;

    private static final BigDecimal PAY_AMOUNT = new BigDecimal("9.90");

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private RefundRequestMapper refundRequestMapper;

    @Mock private PaymentGateway paymentGateway;

    @Mock private EntitlementService entitlementService;

    private RefundServiceImpl refundService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        refundService =
                new RefundServiceImpl(
                        tradeOrderMapper,
                        paymentMapper,
                        refundRequestMapper,
                        paymentGateway,
                        entitlementService);
    }

    /**
     * A paid order is refunded once and the entitlement revocation is recorded.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void refundsPaidOrderAndRecordsRevocation() {
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PAID, USER_ID));
        when(refundRequestMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.insert(any(RefundRequest.class)))
                .thenAnswer(
                        invocation -> {
                            RefundRequest saved = invocation.getArgument(0);
                            saved.setId(REFUND_ID);
                            return 1;
                        });
        when(paymentGateway.refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT)))
                .thenReturn(CHANNEL_REFUND_NO);
        when(refundRequestMapper.markSuccess(eq(REFUND_ID), eq(CHANNEL_REFUND_NO), any()))
                .thenReturn(1);
        when(tradeOrderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);

        RefundView view = refundService.refund(USER_ID, ORDER_ID, request(null));

        assertEquals(REFUND_NO, view.getRefundNo());
        assertEquals(ORDER_ID, view.getOrderId());
        assertEquals(ORDER_NO, view.getOrderNo());
        assertEquals(PAY_AMOUNT, view.getAmount());
        assertEquals(RefundStatus.SUCCESS, view.getStatus());
        assertNotNull(view.getRefundedAt());
        verify(tradeOrderMapper).markRefunded(eq(ORDER_ID), any());
        verify(entitlementService).revoke(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
    }

    /**
     * A second refund for the same order returns the first result and never calls the channel
     * again.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void repeatedRefundReplaysOriginalWithoutCallingChannel() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDED, USER_ID));
        when(refundRequestMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(succeededRefund());

        RefundView view = refundService.refund(USER_ID, ORDER_ID, request(null));

        assertEquals(REFUND_NO, view.getRefundNo());
        assertEquals(RefundStatus.SUCCESS, view.getStatus());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
        verify(tradeOrderMapper, never()).markRefunded(anyLong(), any());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * Refunding more than the paid amount is rejected before any channel call.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsAmountAbovePaidAmount() {
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PAID, USER_ID));
        when(refundRequestMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                refundService.refund(
                                        USER_ID, ORDER_ID, request(new BigDecimal("10.00"))));

        assertEquals(ErrorCode.REFUND_AMOUNT_INVALID.getCode(), exception.getCode());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * A partial refund is not supported in the first release and is rejected the same way.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsPartialAmount() {
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PAID, USER_ID));
        when(refundRequestMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                refundService.refund(
                                        USER_ID, ORDER_ID, request(new BigDecimal("5.00"))));

        assertEquals(ErrorCode.REFUND_AMOUNT_INVALID.getCode(), exception.getCode());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
    }

    /**
     * An unpaid order cannot be refunded.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOrderThatIsNotPaid() {
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING, USER_ID));
        when(refundRequestMapper.selectByOrderIdForUpdate(ORDER_ID)).thenReturn(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.refund(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.ORDER_NOT_REFUNDABLE.getCode(), exception.getCode());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
    }

    /**
     * Another buyer's order is reported as missing, so ids cannot be probed.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOrderOwnedByAnotherUser() {
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PAID, 2002L));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.refund(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    private ApplyRefundRequest request(BigDecimal amount) {
        ApplyRefundRequest request = new ApplyRefundRequest();
        request.setAmount(amount);
        request.setReason("changed my mind");
        return request;
    }

    private TradeOrder order(int status, long userId) {
        TradeOrder order = new TradeOrder();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(userId);
        order.setPayAmount(PAY_AMOUNT);
        order.setStatus(status);
        order.setChannel(1);
        return order;
    }

    private PaymentRecord payment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(3L);
        payment.setOrderId(ORDER_ID);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(PAY_AMOUNT);
        return payment;
    }

    private TradeOrderItem item() {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(ORDER_ID);
        item.setCourseId(COURSE_ID);
        return item;
    }

    private RefundRequest succeededRefund() {
        RefundRequest refund = new RefundRequest();
        refund.setId(REFUND_ID);
        refund.setRefundNo(REFUND_NO);
        refund.setOrderId(ORDER_ID);
        refund.setUserId(USER_ID);
        refund.setAmount(PAY_AMOUNT);
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setChannelRefundNo(CHANNEL_REFUND_NO);
        refund.setRefundedAt(LocalDateTime.now().minusMinutes(1));
        return refund;
    }
}
