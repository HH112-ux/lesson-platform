package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.LearnProgressClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.CourseProgressSummary;
import com.lp.trade.dto.RefundView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.AuditOutbox;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.AuditOutboxMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for refund admission, review and idempotency; persistence and the channel are mocked.
 *
 * <p>The cases follow the product rule D6: within seven days and at most twenty percent progress an
 * application creates a pending request; outside it fails with {@code 62007} and writes nothing.
 * Operations approve before the channel is called, rejection keeps access, and a channel failure
 * returns the order to paid.
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

    private static final long REVIEWER_ID = 9001L;

    private static final String ORDER_NO = "LP20260101000000001";

    private static final String REFUND_NO = "RF" + ORDER_NO;

    private static final String CHANNEL_REFUND_NO = "MOCK-" + REFUND_NO;

    private static final BigDecimal PAY_AMOUNT = new BigDecimal("9.90");

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private PaymentMapper paymentMapper;

    @Mock private RefundRequestMapper refundRequestMapper;

    @Mock private PaymentGateway paymentGateway;

    @Mock private EntitlementService entitlementService;

    @Mock private LearnProgressClient learnProgressClient;

    @Mock private AuditOutboxMapper auditOutboxMapper;

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
                        entitlementService,
                        learnProgressClient,
                        new TransactionTemplate(new SynchronousTransactionManager()),
                        auditOutboxMapper);
    }

    /**
     * An eligible application creates a pending request and never calls the channel.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsPendingRequestWithoutCallingChannel() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(learnProgressClient.progress(USER_ID, COURSE_ID)).thenReturn(progress(20));
        when(refundRequestMapper.countByOrderId(ORDER_ID)).thenReturn(0);
        when(refundRequestMapper.insert(any(RefundRequest.class)))
                .thenAnswer(
                        invocation -> {
                            RefundRequest saved = invocation.getArgument(0);
                            saved.setId(REFUND_ID);
                            return 1;
                        });

        RefundView view = refundService.apply(USER_ID, ORDER_ID, request(null));

        assertEquals(REFUND_NO, view.getRefundNo());
        assertEquals(RefundStatus.PENDING_REVIEW, view.getStatus());
        assertEquals(990L, view.getAmountFen());
        assertNull(view.getRefundedAt());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(tradeOrderMapper, never()).markRefunding(anyLong());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * Applications older than seven days are rejected with {@code 62007} and write nothing.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsApplicationAfterSevenDays() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(8)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.REFUND_ADMISSION_NOT_MET.getCode(), exception.getCode());
        verify(learnProgressClient, never()).progress(anyLong(), anyLong());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * Applications above twenty percent progress are rejected with {@code 62007}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsApplicationAboveTwentyPercentProgress() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(learnProgressClient.progress(USER_ID, COURSE_ID)).thenReturn(progress(21));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.REFUND_ADMISSION_NOT_MET.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * When learn-service cannot answer the application fails closed with {@code 50201}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void failsClosedWhenProgressIsUnavailable() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(learnProgressClient.progress(USER_ID, COURSE_ID))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.CHANNEL_UPSTREAM_ERROR.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * A repeated application returns the pending request without inserting another row.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void repeatedApplicationReturnsPendingRequest() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(pendingRefund());

        RefundView view = refundService.apply(USER_ID, ORDER_ID, request(null));

        assertEquals(REFUND_NO, view.getRefundNo());
        assertEquals(RefundStatus.PENDING_REVIEW, view.getStatus());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
        verify(paymentGateway, never()).refund(any(), anyString(), any());
    }

    /**
     * Approving a pending request calls the channel, marks the refund successful and revokes access
     * only after the channel confirms.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void approvingRefundsAndRevokesEntitlement() {
        RefundRequest pending = pendingRefund();
        RefundRequest refunded = refundedRefund();
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(pending, pending, refunded);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markApproved(eq(REFUND_ID), eq(REVIEWER_ID), any())).thenReturn(1);
        when(tradeOrderMapper.markRefunding(ORDER_ID)).thenReturn(1);
        when(paymentGateway.refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT)))
                .thenReturn(CHANNEL_REFUND_NO);
        when(refundRequestMapper.markSuccess(
                        eq(REFUND_ID), eq(CHANNEL_REFUND_NO), eq("review:" + REVIEWER_ID), any()))
                .thenReturn(1);
        when(tradeOrderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);
        when(paymentMapper.markRefunded(3L)).thenReturn(1);

        RefundView view =
                refundService.review(
                        REFUND_ID, true, null, REVIEWER_ID, OperatorRole.OPS, "trace-1");

        assertEquals(RefundStatus.REFUNDED, view.getStatus());
        assertNotNull(view.getRefundedAt());
        InOrder order = inOrder(refundRequestMapper, paymentGateway);
        order.verify(refundRequestMapper).markApproved(eq(REFUND_ID), eq(REVIEWER_ID), any());
        order.verify(paymentGateway)
                .refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT));
        verify(paymentMapper).markRefunded(3L);
        verify(entitlementService).revoke(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
        AuditOutbox outbox = captureAuditIntent();
        assertEquals(2, outbox.getActionType());
        assertEquals(1, outbox.getObjectType());
        assertEquals(REFUND_ID, outbox.getObjectId());
        assertEquals(0, outbox.getFromStatus());
        assertEquals(1, outbox.getToStatus());
        assertEquals(OperatorRole.OPS, outbox.getOperatorRole());
        assertEquals("trace-1", outbox.getTraceId());
        assertTrue(outbox.getDedupeKey().startsWith("refund-review:" + REFUND_ID + ":"));
    }

    /**
     * A rejection without a reason is refused before any write.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectionRequiresReason() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(pendingRefund());
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                refundService.review(
                                        REFUND_ID,
                                        false,
                                        "  ",
                                        REVIEWER_ID,
                                        OperatorRole.OPS,
                                        "trace-1"));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).markRejected(anyLong(), anyString(), anyLong(), any());
        verify(auditOutboxMapper, never()).insert(any(AuditOutbox.class));
    }

    /**
     * A rejection stores the reason, keeps the order paid and keeps access.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectionKeepsOrderPaidAndEntitlementActive() {
        RefundRequest pending = pendingRefund();
        RefundRequest rejected = rejectedRefund();
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(pending, rejected);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));
        when(refundRequestMapper.markRejected(
                        eq(REFUND_ID), eq("progress too high"), eq(REVIEWER_ID), any()))
                .thenReturn(1);

        RefundView view =
                refundService.review(
                        REFUND_ID,
                        false,
                        "progress too high",
                        REVIEWER_ID,
                        OperatorRole.OPS,
                        "trace-1");

        assertEquals(RefundStatus.REJECTED, view.getStatus());
        assertEquals("progress too high", view.getReviewReason());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        AuditOutbox outbox = captureAuditIntent();
        assertEquals(3, outbox.getActionType());
        assertEquals(3, outbox.getToStatus());
        assertEquals("progress too high", outbox.getRemark());
        verify(tradeOrderMapper, never()).markRefunding(anyLong());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * A duplicated review returns the stored decision and never calls the channel twice.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void repeatedReviewReturnsStoredDecision() {
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(refundedRefund());
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));

        RefundView view =
                refundService.review(
                        REFUND_ID, true, null, REVIEWER_ID, OperatorRole.OPS, "trace-1");

        assertEquals(RefundStatus.REFUNDED, view.getStatus());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(auditOutboxMapper, never()).insert(any(AuditOutbox.class));
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * An explicit channel rejection stores the reason, returns the order to paid and keeps access.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void channelRejectionReturnsOrderToPaid() {
        RefundRequest pending = pendingRefund();
        RefundRequest failed = failedRefund();
        failed.setFailReason("channel rejected: BizException: channel down");
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(pending, pending, failed);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markApproved(eq(REFUND_ID), eq(REVIEWER_ID), any())).thenReturn(1);
        when(tradeOrderMapper.markRefunding(ORDER_ID)).thenReturn(1);
        when(paymentGateway.refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT)))
                .thenThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR, "channel down"));
        when(refundRequestMapper.markFailed(eq(REFUND_ID), anyString())).thenReturn(1);
        when(tradeOrderMapper.markPaidAfterRefundFailure(ORDER_ID)).thenReturn(1);

        RefundView view =
                refundService.review(
                        REFUND_ID, true, null, REVIEWER_ID, OperatorRole.OPS, "trace-1");

        assertEquals(RefundStatus.FAILED, view.getStatus());
        assertEquals("channel rejected: BizException: channel down", view.getFailReason());
        verify(tradeOrderMapper).markPaidAfterRefundFailure(ORDER_ID);
        verify(refundRequestMapper, never()).markUnknown(anyLong(), anyString());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * A timeout leaves the channel outcome unknown, so the order stays refunding and cannot be
     * applied for again.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void channelTimeoutKeepsOrderRefundingAndMarksResultUnknown() {
        RefundRequest pending = pendingRefund();
        RefundRequest unknown = refund(RefundStatus.UNKNOWN);
        unknown.setFailReason("result unknown: RuntimeException: Read timed out");
        when(refundRequestMapper.selectById(REFUND_ID)).thenReturn(pending, pending, unknown);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markApproved(eq(REFUND_ID), eq(REVIEWER_ID), any())).thenReturn(1);
        when(tradeOrderMapper.markRefunding(ORDER_ID)).thenReturn(1);
        when(paymentGateway.refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT)))
                .thenThrow(new RuntimeException("Read timed out"));
        when(refundRequestMapper.markUnknown(eq(REFUND_ID), anyString())).thenReturn(1);

        RefundView view =
                refundService.review(
                        REFUND_ID, true, null, REVIEWER_ID, OperatorRole.OPS, "trace-1");

        assertEquals(RefundStatus.UNKNOWN, view.getStatus());
        assertEquals("result unknown: RuntimeException: Read timed out", view.getFailReason());
        verify(refundRequestMapper)
                .markUnknown(REFUND_ID, "result unknown: RuntimeException: Read timed out");
        verify(refundRequestMapper, never()).markFailed(anyLong(), anyString());
        verify(tradeOrderMapper, never()).markPaidAfterRefundFailure(anyLong());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * While the channel call is blocked another reader already observes the approved status.
     *
     * @throws Exception when the threads are interrupted
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void approvedStatusIsVisibleWhileChannelIsStillRunning() throws Exception {
        AtomicInteger storedStatus = new AtomicInteger(RefundStatus.PENDING_REVIEW);
        when(refundRequestMapper.selectById(REFUND_ID))
                .thenAnswer(
                        invocation -> {
                            RefundRequest snapshot = pendingRefund();
                            snapshot.setStatus(storedStatus.get());
                            return snapshot;
                        });
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(paidOrder(LocalDateTime.now()));
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(refundRequestMapper.markApproved(eq(REFUND_ID), eq(REVIEWER_ID), any()))
                .thenAnswer(
                        invocation -> {
                            storedStatus.compareAndSet(
                                    RefundStatus.PENDING_REVIEW, RefundStatus.APPROVED);
                            return 1;
                        });
        when(tradeOrderMapper.markRefunding(ORDER_ID)).thenReturn(1);
        CountDownLatch channelEntered = new CountDownLatch(1);
        CountDownLatch releaseChannel = new CountDownLatch(1);
        when(paymentGateway.refund(any(PaymentRecord.class), eq(REFUND_NO), eq(PAY_AMOUNT)))
                .thenAnswer(
                        invocation -> {
                            channelEntered.countDown();
                            assertTrue(releaseChannel.await(5, TimeUnit.SECONDS));
                            return CHANNEL_REFUND_NO;
                        });
        when(refundRequestMapper.markSuccess(
                        eq(REFUND_ID), eq(CHANNEL_REFUND_NO), eq("review:" + REVIEWER_ID), any()))
                .thenAnswer(
                        invocation -> {
                            storedStatus.set(RefundStatus.REFUNDED);
                            return 1;
                        });
        when(tradeOrderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);
        when(paymentMapper.markRefunded(3L)).thenReturn(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RefundView> review =
                    pool.submit(
                            () ->
                                    refundService.review(
                                            REFUND_ID,
                                            true,
                                            null,
                                            REVIEWER_ID,
                                            OperatorRole.OPS,
                                            "trace-1"));
            assertTrue(channelEntered.await(5, TimeUnit.SECONDS));
            Future<Integer> observed =
                    pool.submit(() -> refundRequestMapper.selectById(REFUND_ID).getStatus());
            assertEquals(RefundStatus.APPROVED, observed.get(5, TimeUnit.SECONDS));
            releaseChannel.countDown();
            assertEquals(RefundStatus.REFUNDED, review.get(5, TimeUnit.SECONDS).getStatus());
        } finally {
            releaseChannel.countDown();
            pool.shutdownNow();
        }
    }

    /**
     * Losing the unique key race returns the in-progress refund instead of a {@code 500}.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void concurrentApplicationReturnsInProgressRefund() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null, pendingRefund());
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());
        when(learnProgressClient.progress(USER_ID, COURSE_ID)).thenReturn(progress(20));
        when(refundRequestMapper.countByOrderId(ORDER_ID)).thenReturn(0);
        when(refundRequestMapper.insert(any(RefundRequest.class)))
                .thenThrow(new DuplicateKeyException("uk_active_order"));

        RefundView view = refundService.apply(USER_ID, ORDER_ID, request(null));

        assertEquals(REFUND_NO, view.getRefundNo());
        assertEquals(RefundStatus.PENDING_REVIEW, view.getStatus());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
    }

    /**
     * An amount above or below the paid amount is rejected before any row is written.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsAmountThatDiffersFromPaidAmount() {
        when(tradeOrderMapper.selectById(ORDER_ID))
                .thenReturn(paidOrder(LocalDateTime.now().minusDays(1)));
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(payment());
        when(tradeOrderMapper.selectFirstItem(ORDER_ID)).thenReturn(item());

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(1000L)));

        assertEquals(ErrorCode.REFUND_AMOUNT_INVALID.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * An unpaid order cannot be refunded.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOrderThatIsNotPaid() {
        TradeOrder order = paidOrder(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(refundRequestMapper.selectActiveByOrderId(ORDER_ID)).thenReturn(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.ORDER_NOT_REFUNDABLE.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    /**
     * Another buyer's order is reported as missing, so ids cannot be probed.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOrderOwnedByAnotherUser() {
        TradeOrder order = paidOrder(LocalDateTime.now());
        order.setUserId(2002L);
        when(tradeOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> refundService.apply(USER_ID, ORDER_ID, request(null)));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).insert(any(RefundRequest.class));
    }

    private ApplyRefundRequest request(Long amountFen) {
        ApplyRefundRequest request = new ApplyRefundRequest();
        request.setAmountFen(amountFen);
        request.setReason("changed my mind");
        return request;
    }

    private TradeOrder paidOrder(LocalDateTime paidAt) {
        TradeOrder order = new TradeOrder();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setPayAmount(PAY_AMOUNT);
        order.setStatus(OrderStatus.PAID);
        order.setChannel(1);
        order.setPaidAt(paidAt);
        return order;
    }

    private PaymentRecord payment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(3L);
        payment.setOrderId(ORDER_ID);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(PAY_AMOUNT);
        payment.setChannel(1);
        return payment;
    }

    private TradeOrderItem item() {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(ORDER_ID);
        item.setCourseId(COURSE_ID);
        return item;
    }

    private CourseProgressSummary progress(int percent) {
        return new CourseProgressSummary(percent, 100, percent);
    }

    private AuditOutbox captureAuditIntent() {
        ArgumentCaptor<AuditOutbox> captor = ArgumentCaptor.forClass(AuditOutbox.class);
        verify(auditOutboxMapper).insert(captor.capture());
        return captor.getValue();
    }

    private RefundRequest pendingRefund() {
        RefundRequest refund = refund(RefundStatus.PENDING_REVIEW);
        return refund;
    }

    private RefundRequest refundedRefund() {
        RefundRequest refund = refund(RefundStatus.REFUNDED);
        refund.setChannelRefundNo(CHANNEL_REFUND_NO);
        refund.setRefundedAt(LocalDateTime.now().minusMinutes(1));
        return refund;
    }

    private RefundRequest rejectedRefund() {
        RefundRequest refund = refund(RefundStatus.REJECTED);
        refund.setReviewReason("progress too high");
        refund.setReviewedBy(REVIEWER_ID);
        refund.setReviewedAt(LocalDateTime.now());
        return refund;
    }

    private RefundRequest failedRefund() {
        RefundRequest refund = refund(RefundStatus.FAILED);
        refund.setFailReason("channel down");
        return refund;
    }

    private RefundRequest refund(int status) {
        RefundRequest refund = new RefundRequest();
        refund.setId(REFUND_ID);
        refund.setRefundNo(REFUND_NO);
        refund.setOrderId(ORDER_ID);
        refund.setUserId(USER_ID);
        refund.setAmount(PAY_AMOUNT);
        refund.setChannel(1);
        refund.setReason("changed my mind");
        refund.setStatus(status);
        return refund;
    }

    /**
     * Runs every callback synchronously so the commit-then-call order remains observable.
     *
     * @author Caspian
     * @since 0.2.0
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
