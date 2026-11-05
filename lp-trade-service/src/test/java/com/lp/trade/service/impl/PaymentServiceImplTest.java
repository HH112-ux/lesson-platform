package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.payment.SignatureVerifier;
import com.lp.trade.payment.UnconfiguredSignatureVerifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for callback handling; persistence, channel ports and entitlement are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long ORDER_ID = 1L;

    private static final long COURSE_ID = 7L;

    private static final String ORDER_NO = "LP1";

    private static final String TRADE_NO = "T1";

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private PaymentGateway paymentGateway;

    @Mock private SignatureVerifier signatureVerifier;

    @Mock private EntitlementService entitlementService;

    private PaymentServiceImpl paymentService;

    /**
     * Builds the service under test with a passing verifier.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        paymentService =
                new PaymentServiceImpl(
                        tradeOrderMapper,
                        paymentMapper,
                        paymentGateway,
                        signatureVerifier,
                        entitlementService);
    }

    /**
     * A duplicated callback grants ownership only once.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void repeatedCallbackIsIdempotent() {
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.ALIPAY, TRADE_NO))
                .thenReturn(null, succeededPayment());
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(pendingPayment());
        when(paymentMapper.markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenReturn(1);
        when(tradeOrderMapper.markPaid(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));
        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));

        verify(entitlementService, times(1)).grant(USER_ID, COURSE_ID, ORDER_NO);
        verify(paymentMapper, times(1)).markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class));
    }

    /**
     * A callback amount that differs from the order amount is rejected.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsAmountMismatch() {
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.ALIPAY, TRADE_NO))
                .thenReturn(null);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(pendingPayment());

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                paymentService.handleNotify(
                                        PaymentChannel.ALIPAY,
                                        Map.of(),
                                        "body",
                                        notification("1.00")));

        assertEquals(62009, exception.getCode());
        verify(paymentMapper, never())
                .markSuccess(anyLong(), anyString(), any(LocalDateTime.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyString());
    }

    /**
     * Without configured credentials every callback fails explicitly instead of passing.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void unconfiguredVerifierFailsExplicitly() {
        PaymentServiceImpl unconfigured =
                new PaymentServiceImpl(
                        tradeOrderMapper,
                        paymentMapper,
                        paymentGateway,
                        new UnconfiguredSignatureVerifier(),
                        entitlementService);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                unconfigured.handleNotify(
                                        PaymentChannel.ALIPAY,
                                        Map.of(),
                                        "body",
                                        notification("99.00")));

        assertEquals(50205, exception.getCode());
        verifyNoInteractions(paymentMapper, tradeOrderMapper, entitlementService);
    }

    /**
     * A callback from the other channel is rejected before any state change.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsChannelMismatch() {
        PaymentNotification notification = notification("99.00");
        notification.setChannel(PaymentChannel.WXPAY);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                paymentService.handleNotify(
                                        PaymentChannel.ALIPAY, Map.of(), "body", notification));

        assertEquals(62010, exception.getCode());
        verifyNoInteractions(paymentMapper, tradeOrderMapper, entitlementService);
    }

    /**
     * An order that belongs to another user is reported as missing, never paid.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOrderOfAnotherUser() {
        TradeOrder otherUsersOrder = order();
        otherUsersOrder.setUserId(2002L);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(otherUsersOrder);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> paymentService.initiate(USER_ID, "web", ORDER_NO));

        assertEquals(40401, exception.getCode());
        verifyNoInteractions(paymentMapper, paymentGateway);
    }

    private PaymentNotification notification(String amount) {
        PaymentNotification notification = new PaymentNotification();
        notification.setOrderNo(ORDER_NO);
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setChannelTradeNo(TRADE_NO);
        notification.setAmount(new BigDecimal(amount));
        return notification;
    }

    private TradeOrder order() {
        TradeOrder order = new TradeOrder();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setChannel(PaymentChannel.ALIPAY);
        order.setPayAmount(new BigDecimal("99.00"));
        return order;
    }

    private PaymentRecord pendingPayment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(1L);
        payment.setOrderId(ORDER_ID);
        payment.setChannel(PaymentChannel.ALIPAY);
        payment.setAmount(new BigDecimal("99.00"));
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private PaymentRecord succeededPayment() {
        PaymentRecord payment = pendingPayment();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setChannelTradeNo(TRADE_NO);
        return payment;
    }

    private TradeOrderItem item() {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(ORDER_ID);
        item.setCourseId(COURSE_ID);
        return item;
    }
}
