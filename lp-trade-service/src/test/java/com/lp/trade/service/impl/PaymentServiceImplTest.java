package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.dto.PayView;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.payment.SignatureVerifier;
import com.lp.trade.payment.UnconfiguredPaymentGateway;
import com.lp.trade.payment.UnconfiguredSignatureVerifier;
import com.lp.trade.service.AccountDeletionGuard;
import com.lp.trade.service.PaymentAnomalyRecorder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Mock private TransactionTemplate transactionTemplate;

    @Mock private AccountDeletionGuard accountDeletionGuard;

    @Mock private PaymentAnomalyRecorder anomalyRecorder;

    private PaymentServiceImpl paymentService;

    /**
     * Builds the service under test with a passing verifier and an immediate transaction.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        lenient()
                .when(
                        transactionTemplate.execute(
                                ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(
                        invocation -> {
                            TransactionCallback<Object> callback = invocation.getArgument(0);
                            return callback.doInTransaction(null);
                        });
        paymentService =
                new PaymentServiceImpl(
                        tradeOrderMapper,
                        paymentMapper,
                        paymentGateway,
                        signatureVerifier,
                        entitlementService,
                        transactionTemplate,
                        accountDeletionGuard,
                        anomalyRecorder);
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

        verify(entitlementService, times(1)).grant(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
        verify(paymentMapper, times(1)).markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class));
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
                        entitlementService,
                        transactionTemplate,
                        accountDeletionGuard,
                        anomalyRecorder);

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
        when(tradeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(otherUsersOrder);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> paymentService.initiate(USER_ID, "web", ORDER_NO));

        assertEquals(40401, exception.getCode());
        verifyNoInteractions(paymentMapper, paymentGateway);
    }

    /**
     * A payable order receives the gateway payload and a new payment attempt is persisted.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void initiateReturnsGatewayPrepayPayload() {
        when(tradeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insertPayment(any(PaymentRecord.class))).thenReturn(1);
        when(paymentGateway.prepay(any(PaymentRecord.class)))
                .thenReturn(
                        new PrepayResult(
                                "PY1", PaymentChannel.ALIPAY, Map.of("orderInfo", "form")));

        PayView view = paymentService.initiate(USER_ID, "web", ORDER_NO);

        assertEquals("PY1", view.getPayNo());
        assertEquals(PaymentChannel.ALIPAY, view.getChannel());
        assertEquals("form", view.getPayParams().get("orderInfo"));
        verify(paymentMapper, times(1)).insertPayment(any(PaymentRecord.class));
    }

    /**
     * A buyer inside the cooling-off period cannot start a payment, even for an existing order.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsInitiateDuringCoolingOff() {
        doThrow(new BizException(ErrorCode.ACCOUNT_COOLING_OFF_RESTRICTED))
                .when(accountDeletionGuard)
                .assertNotCoolingOff(USER_ID);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> paymentService.initiate(USER_ID, "web", ORDER_NO));

        assertEquals(67005, exception.getCode());
        verifyNoInteractions(tradeOrderMapper, paymentMapper, paymentGateway);
    }

    /**
     * Without configured credentials payment initiation fails explicitly, never with a payload.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void unconfiguredGatewayFailsExplicitly() {
        PaymentServiceImpl unconfiguredGateway =
                new PaymentServiceImpl(
                        tradeOrderMapper,
                        paymentMapper,
                        new UnconfiguredPaymentGateway(),
                        signatureVerifier,
                        entitlementService,
                        transactionTemplate,
                        accountDeletionGuard,
                        anomalyRecorder);
        when(tradeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.insertPayment(any(PaymentRecord.class))).thenReturn(1);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> unconfiguredGateway.initiate(USER_ID, "web", ORDER_NO));

        assertEquals(50205, exception.getCode());
    }

    /**
     * A closed order cannot start a new payment.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsClosedOrder() {
        TradeOrder closedOrder = order();
        closedOrder.setStatus(OrderStatus.CLOSED);
        when(tradeOrderMapper.selectByOrderNoForUpdate(ORDER_NO)).thenReturn(closedOrder);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> paymentService.initiate(USER_ID, "web", ORDER_NO));

        assertEquals(62001, exception.getCode());
        verifyNoInteractions(paymentMapper, paymentGateway);
    }

    /**
     * The retired miniapp channel is refused with a dedicated code, not a configuration error.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMiniappInitiate() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> paymentService.initiate(USER_ID, "miniapp", ORDER_NO));

        assertEquals(62011, exception.getCode());
        verifyNoInteractions(paymentMapper, paymentGateway, tradeOrderMapper);
    }

    /**
     * Historical WeChat Pay rows stay readable and reconcilable after the channel was removed.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void reconcilesHistoricalWxpayNotification() {
        TradeOrder wxpayOrder = order();
        wxpayOrder.setChannel(PaymentChannel.WXPAY);
        PaymentRecord wxpayPayment = pendingPayment();
        wxpayPayment.setChannel(PaymentChannel.WXPAY);
        PaymentNotification notification = notification("99.00");
        notification.setChannel(PaymentChannel.WXPAY);
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.WXPAY, TRADE_NO)).thenReturn(null);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(wxpayOrder);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(wxpayPayment);
        when(paymentMapper.markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenReturn(1);
        when(tradeOrderMapper.markPaid(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());

        paymentService.handleNotify(PaymentChannel.WXPAY, Map.of(), "body", notification);

        verify(entitlementService).grant(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
        assertEquals(2, PaymentChannel.WXPAY);
    }

    /**
     * A success callback that arrives after the attempt was closed is recorded for refund
     * compensation instead of being dropped, and it never grants ownership.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void recordsLateCallbackOnClosedAttemptForCompensation() {
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.ALIPAY, TRADE_NO))
                .thenReturn(null);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(pendingPayment());
        when(paymentMapper.markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenReturn(0);
        when(paymentMapper.selectById(1L)).thenReturn(closedPayment());
        when(paymentMapper.markPaidAfterClose(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenReturn(1);

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));

        verify(paymentMapper, times(1))
                .markPaidAfterClose(eq(1L), eq(TRADE_NO), any(LocalDateTime.class));
        verify(tradeOrderMapper, never()).markPaid(anyLong(), any(LocalDateTime.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * A retried callback on an attempt that another callback already completed stays a no-op.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void ignoresRepeatedCallbackWhenAttemptAlreadySucceeded() {
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.ALIPAY, TRADE_NO))
                .thenReturn(null);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(pendingPayment());
        when(paymentMapper.markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenReturn(0);
        when(paymentMapper.selectById(1L)).thenReturn(succeededPayment());

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));

        verify(paymentMapper, never())
                .markPaidAfterClose(anyLong(), anyString(), any(LocalDateTime.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void ignoresCallbackWithUnacceptedTradeStatus() {
        for (String status : new String[] {"WAIT_BUYER_PAY", null}) {
            PaymentNotification ignored = notification("99.00");
            ignored.setTradeStatus(status);
            paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", ignored);
        }

        verifyNoInteractions(paymentMapper, anomalyRecorder);
    }

    @Test
    void recordsAmountMismatchAndAcknowledges() {
        stubPendingCallback(order());

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("88.00"));

        verifyAnomaly(PaymentAnomalyRecorder.AMOUNT_MISMATCH, "99.00", "88.00");
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
    }

    @Test
    void recordsMissingOrderAndRejects() {
        stubPendingCallback(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                paymentService.handleNotify(
                                        PaymentChannel.ALIPAY,
                                        Map.of(),
                                        "body",
                                        notification("99.00")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verifyAnomaly(PaymentAnomalyRecorder.ORDER_MISSING, null, "99.00");
    }

    @ParameterizedTest
    @ValueSource(ints = {OrderStatus.PAID, OrderStatus.CLOSED, OrderStatus.CANCELLED})
    void recordsUnpayableOrderAndAcknowledges(int status) {
        TradeOrder existing = order();
        existing.setStatus(status);
        stubPendingCallback(existing);

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));

        verifyAnomaly(PaymentAnomalyRecorder.ORDER_NOT_PAYABLE, "99.00", "99.00");
        verify(paymentMapper, never()).markSuccess(anyLong(), anyString(), any());
    }

    @Test
    void treatsConcurrentDuplicateTradeNumberAsHandled() {
        stubPendingCallback(order());
        when(paymentMapper.markSuccess(eq(1L), eq(TRADE_NO), any(LocalDateTime.class)))
                .thenThrow(new DuplicateKeyException("uk_channel_trade"));

        paymentService.handleNotify(PaymentChannel.ALIPAY, Map.of(), "body", notification("99.00"));

        verify(tradeOrderMapper, never()).markPaid(anyLong(), any(LocalDateTime.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    private void stubPendingCallback(TradeOrder order) {
        when(paymentMapper.selectByChannelTradeNo(PaymentChannel.ALIPAY, TRADE_NO))
                .thenReturn(null);
        when(tradeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        lenient().when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(pendingPayment());
    }

    private void verifyAnomaly(int reason, String expected, String notified) {
        verify(anomalyRecorder)
                .record(
                        argThat(
                                n ->
                                        n.getChannel() == PaymentChannel.ALIPAY
                                                && ORDER_NO.equals(n.getOrderNo())
                                                && TRADE_NO.equals(n.getChannelTradeNo())
                                                && new BigDecimal(notified).equals(n.getAmount())
                                                && "TRADE_SUCCESS".equals(n.getTradeStatus())),
                        expected == null
                                ? ArgumentMatchers.<BigDecimal>isNull()
                                : eq(new BigDecimal(expected)),
                        eq(reason),
                        anyString());
    }

    private PaymentNotification notification(String amount) {
        PaymentNotification notification = new PaymentNotification();
        notification.setOrderNo(ORDER_NO);
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setChannelTradeNo(TRADE_NO);
        notification.setAmount(new BigDecimal(amount));
        notification.setTradeStatus("TRADE_SUCCESS");
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

    private PaymentRecord closedPayment() {
        PaymentRecord payment = pendingPayment();
        payment.setStatus(PaymentStatus.CLOSED);
        return payment;
    }

    private TradeOrderItem item() {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(ORDER_ID);
        item.setCourseId(COURSE_ID);
        return item;
    }
}
