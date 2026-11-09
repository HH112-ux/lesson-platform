package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.lp.trade.client.LearnProgressClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.CourseProgressSummary;
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
                        learnProgressClient);
    }

    /**
     * An eligible application creates a pending request and never calls the channel.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsPendingRequestWithoutCallingChannel() {
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
        verify(tradeOrderMapper, never()).markRefunding(anyLong());
    }

    /**
     * Applications older than seven days are rejected with {@code 62007} and write nothing.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsApplicationAfterSevenDays() {
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        when(refundRequestMapper.markSuccess(eq(REFUND_ID), eq(CHANNEL_REFUND_NO), any()))
                .thenReturn(1);
        when(tradeOrderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);

        RefundView view = refundService.review(REFUND_ID, true, null, REVIEWER_ID);

        assertEquals(RefundStatus.REFUNDED, view.getStatus());
        assertNotNull(view.getRefundedAt());
        verify(entitlementService).revoke(USER_ID, COURSE_ID, ORDER_ID, ORDER_NO);
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
                        () -> refundService.review(REFUND_ID, false, "  ", REVIEWER_ID));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
        verify(refundRequestMapper, never()).markRejected(anyLong(), anyString(), anyLong(), any());
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

        RefundView view = refundService.review(REFUND_ID, false, "progress too high", REVIEWER_ID);

        assertEquals(RefundStatus.REJECTED, view.getStatus());
        assertEquals("progress too high", view.getReviewReason());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
        verify(tradeOrderMapper, never()).markRefunding(anyLong());
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

        RefundView view = refundService.review(REFUND_ID, true, null, REVIEWER_ID);

        assertEquals(RefundStatus.REFUNDED, view.getStatus());
        verify(paymentGateway, never()).refund(any(), anyString(), any());
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * A channel failure stores the reason, returns the order to paid and keeps access.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void channelFailureKeepsOrderPaidAndEntitlementActive() {
        RefundRequest pending = pendingRefund();
        RefundRequest failed = failedRefund();
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

        RefundView view = refundService.review(REFUND_ID, true, null, REVIEWER_ID);

        assertEquals(RefundStatus.FAILED, view.getStatus());
        assertEquals("channel down", view.getFailReason());
        verify(tradeOrderMapper).markPaidAfterRefundFailure(ORDER_ID);
        verify(entitlementService, never()).revoke(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * An amount above or below the paid amount is rejected before any row is written.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsAmountThatDiffersFromPaidAmount() {
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID))
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
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
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
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);

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
}
