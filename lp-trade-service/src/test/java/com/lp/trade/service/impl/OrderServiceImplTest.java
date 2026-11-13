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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.CancelOrderResult;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderDetailView;
import com.lp.trade.dto.OrderPageView;
import com.lp.trade.dto.OrderView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.Coupon;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.entity.UserCoupon;
import com.lp.trade.mapper.CouponMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.service.AccountDeletionGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests for order creation; persistence, course-service and the entitlement port are mocked.
 *
 * <p>Rollback after a failed coupon consumption is guaranteed by {@code @Transactional} on the
 * service; these tests verify the failure is raised and no entitlement is granted.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private CouponMapper couponMapper;

    @Mock private CourseInternalClient courseInternalClient;

    @Mock private EntitlementService entitlementService;

    @Mock private PaymentMapper paymentMapper;

    @Mock private RefundRequestMapper refundRequestMapper;

    @Mock private TransactionTemplate transactionTemplate;

    @Mock private AccountDeletionGuard accountDeletionGuard;

    private OrderServiceImpl orderService;

    /**
     * Wires the service with a transaction template that runs the callback immediately.
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
        orderService =
                new OrderServiceImpl(
                        tradeOrderMapper,
                        couponMapper,
                        courseInternalClient,
                        entitlementService,
                        paymentMapper,
                        refundRequestMapper,
                        transactionTemplate,
                        accountDeletionGuard);
    }

    /**
     * A paid course creates a pending order, snapshots the line and does not grant access yet.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsPendingOrder() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(0L);
        when(tradeOrderMapper.insertOrder(any(TradeOrder.class)))
                .thenAnswer(
                        invocation -> {
                            TradeOrder order = invocation.getArgument(0);
                            order.setId(42L);
                            return 1;
                        });

        OrderView view = orderService.create(USER_ID, "web", request("key-1", null));

        assertEquals(OrderStatus.PENDING, view.getStatus());
        assertEquals(PaymentChannel.ALIPAY, view.getChannel());
        assertEquals(9900L, view.getPayAmountFen());
        assertNotNull(view.getPayExpireAt());
        assertNull(view.getPaidAt());
        assertTrue(view.getOrderNo().startsWith("LP"));
        verify(tradeOrderMapper).insertItem(any(TradeOrderItem.class));
        verify(entitlementService, never()).grant(anyLong(), eq(COURSE_ID), anyLong(), anyString());
    }

    /**
     * Two courses are aggregated into one order with one line per course and a summed total.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void createsOneOrderWithOneLinePerCourse() {
        when(courseInternalClient.course(7L)).thenReturn(courseWithId(7L, 9900L, 2));
        when(courseInternalClient.course(9L)).thenReturn(courseWithId(9L, 19900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        when(tradeOrderMapper.countOwned(USER_ID, 9L)).thenReturn(0L);
        stubInsertedOrder(42L);

        OrderView view =
                orderService.create(USER_ID, "web", combinedRequest("key-20", List.of(9L, 7L)));

        assertEquals(OrderStatus.PENDING, view.getStatus());
        assertEquals(29800L, view.getTotalAmountFen());
        assertEquals(29800L, view.getPayAmountFen());
        assertEquals(7L, view.getCourseId());
        assertEquals(2, view.getItems().size());
        assertEquals(7L, view.getItems().get(0).getCourseId());
        assertEquals(9L, view.getItems().get(1).getCourseId());
        verify(tradeOrderMapper, times(2)).insertItem(any(TradeOrderItem.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * Sending both course fields is ambiguous and rejected before any course is read.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void rejectsCourseIdAndCourseIdsTogether() {
        CreateOrderRequest request = combinedRequest("key-23", List.of(7L, 9L));
        request.setCourseId(COURSE_ID);

        BizException exception =
                assertThrows(
                        BizException.class, () -> orderService.create(USER_ID, "web", request));

        assertEquals(40001, exception.getCode());
        assertEquals("courseId and courseIds must not be sent together", exception.getMessage());
        verifyNoInteractions(courseInternalClient);
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * More than twenty distinct courses exceed the combined order limit.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void rejectsMoreThanTwentyCourses() {
        List<Long> courseIds = LongStream.rangeClosed(1, 21).boxed().toList();

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                orderService.create(
                                        USER_ID, "web", combinedRequest("key-24", courseIds)));

        assertEquals(40001, exception.getCode());
        verifyNoInteractions(courseInternalClient);
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A coupon is not accepted on a combined order in this release.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void rejectsCouponOnCombinedOrder() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                orderService.create(
                                        USER_ID,
                                        "web",
                                        combinedRequest("key-25", List.of(7L, 9L), 55L)));

        assertEquals(40001, exception.getCode());
        assertEquals("coupon is not supported for combined orders", exception.getMessage());
        verifyNoInteractions(courseInternalClient);
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A free combined order completes immediately and grants every course it contains.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void grantsEveryCourseOfAFreeCombinedOrder() {
        when(courseInternalClient.course(7L)).thenReturn(courseWithId(7L, 0L, 2));
        when(courseInternalClient.course(9L)).thenReturn(courseWithId(9L, 0L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        when(tradeOrderMapper.countOwned(USER_ID, 9L)).thenReturn(0L);
        stubInsertedOrder(42L);

        OrderView view =
                orderService.create(USER_ID, "web", combinedRequest("key-21", List.of(7L, 9L)));

        assertEquals(OrderStatus.PAID, view.getStatus());
        verify(entitlementService).grant(eq(USER_ID), eq(7L), anyLong(), anyString());
        verify(entitlementService).grant(eq(USER_ID), eq(9L), anyLong(), anyString());
    }

    /**
     * A repeated course id is sold once, matching the deduplicated idempotency digest.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void deduplicatesRepeatedCourseIds() {
        when(courseInternalClient.course(7L)).thenReturn(courseWithId(7L, 9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        stubInsertedOrder(42L);

        OrderView view =
                orderService.create(USER_ID, "web", combinedRequest("key-22", List.of(7L, 7L)));

        assertEquals(9900L, view.getPayAmountFen());
        assertEquals(1, view.getItems().size());
        verify(tradeOrderMapper).insertItem(any(TradeOrderItem.class));
    }

    /**
     * Replaying a combined order does not depend on the order of the requested course ids.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void replaysCombinedOrderRegardlessOfRequestOrder() {
        TradeOrder existing = order(42L, "LP-COMBINED", OrderStatus.PENDING);
        existing.setTotalAmount(new BigDecimal("198.00"));
        existing.setPayAmount(new BigDecimal("198.00"));
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-26"))
                .thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item(7L), item(9L)));
        when(courseInternalClient.course(7L)).thenReturn(courseWithId(7L, 9900L, 2));
        when(courseInternalClient.course(9L)).thenReturn(courseWithId(9L, 9900L, 2));

        OrderView view =
                orderService.create(USER_ID, "web", combinedRequest("key-26", List.of(9L, 7L)));

        assertEquals("LP-COMBINED", view.getOrderNo());
        assertEquals(2, view.getItems().size());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A combined replay after a course price change is rejected instead of returning the old
     * amount.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void rejectsCombinedReplayAfterPriceChange() {
        TradeOrder existing = order(42L, "LP-COMBINED", OrderStatus.PENDING);
        existing.setTotalAmount(new BigDecimal("198.00"));
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-27"))
                .thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item(7L), item(9L)));
        when(courseInternalClient.course(7L)).thenReturn(courseWithId(7L, 9900L, 2));
        when(courseInternalClient.course(9L)).thenReturn(courseWithId(9L, 29900L, 2));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                orderService.create(
                                        USER_ID,
                                        "web",
                                        combinedRequest("key-27", List.of(7L, 9L))));

        assertEquals(40901, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * Replaying the same idempotency key returns the original order without creating a new one.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void replaysExistingOrder() {
        TradeOrder existing = order(42L, "LP20260101000000001", OrderStatus.PENDING);
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-1")).thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));

        OrderView view = orderService.create(USER_ID, "web", request("key-1", null));

        assertEquals("LP20260101000000001", view.getOrderNo());
        assertEquals(COURSE_ID, view.getCourseId());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
        verify(courseInternalClient, never()).course(COURSE_ID);
    }

    /**
     * A buyer inside the cooling-off period is refused before any row is read or written.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsNewOrderDuringCoolingOff() {
        doThrow(new BizException(ErrorCode.ACCOUNT_COOLING_OFF_RESTRICTED))
                .when(accountDeletionGuard)
                .assertNotCoolingOff(USER_ID);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-10", null)));

        assertEquals(67005, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
        verifyNoInteractions(courseInternalClient);
    }

    /**
     * Replaying an order created before the cooling-off period keeps working, so an idempotent
     * retry never turns a held order into an error.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void replaysExistingOrderWithoutTheCoolingOffGate() {
        TradeOrder existing = order(42L, "LP20260101000000001", OrderStatus.PENDING);
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-11"))
                .thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));

        OrderView view = orderService.create(USER_ID, "web", request("key-11", null));

        assertEquals("LP20260101000000001", view.getOrderNo());
        verifyNoInteractions(accountDeletionGuard);
    }

    /**
     * Reusing an idempotency key for another course is rejected instead of returning the first
     * order, so a stale key cannot buy a different course in the buyer's name.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsReplayedKeyForAnotherCourse() {
        TradeOrder existing = order(42L, "LP20260101000000001", OrderStatus.PENDING);
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-6")).thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));
        CreateOrderRequest request = request("key-6", null);
        request.setCourseId(COURSE_ID + 1);

        BizException exception =
                assertThrows(
                        BizException.class, () -> orderService.create(USER_ID, "web", request));

        assertEquals(40901, exception.getCode());
        assertEquals("idempotency key reused with a different request", exception.getMessage());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * Reusing an idempotency key with another coupon is rejected instead of returning the first
     * order.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void rejectsReplayedKeyWithAnotherCoupon() {
        TradeOrder existing = order(42L, "LP20260101000000001", OrderStatus.PENDING);
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-7")).thenReturn(existing);
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-7", 55L)));

        assertEquals(40901, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A course already bought cannot be ordered again.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOwnedCourse() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(1L);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-2", null)));

        assertEquals(61002, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A zero payable amount completes the order and grants access without any payment.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void completesFreeOrderImmediately() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(0L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(0L);
        when(tradeOrderMapper.insertOrder(any(TradeOrder.class)))
                .thenAnswer(
                        invocation -> {
                            TradeOrder order = invocation.getArgument(0);
                            order.setId(42L);
                            return 1;
                        });

        OrderView view = orderService.create(USER_ID, "web", request("key-3", null));

        assertEquals(OrderStatus.PAID, view.getStatus());
        assertEquals(PaymentChannel.ALIPAY, view.getChannel());
        assertNotNull(view.getPaidAt());
        assertNull(view.getPayExpireAt());
        verify(entitlementService).grant(eq(USER_ID), eq(COURSE_ID), anyLong(), anyString());
    }

    /**
     * The miniapp channel was retired, so ordering from the miniapp fails with a dedicated code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMiniappOrders() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "miniapp", request("key-9", null)));

        assertEquals(62011, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A coupon that was already consumed fails the order instead of silently charging full price.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void failsWhenCouponConsumptionLosesRace() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(0L);
        when(couponMapper.selectUserCoupon(USER_ID, 55L)).thenReturn(userCoupon(55L));
        when(couponMapper.selectCoupon(55L)).thenReturn(coupon());
        when(tradeOrderMapper.insertOrder(any(TradeOrder.class)))
                .thenAnswer(
                        invocation -> {
                            TradeOrder order = invocation.getArgument(0);
                            order.setId(42L);
                            return 1;
                        });
        when(couponMapper.markUsed(eq(55L), eq(42L), any(LocalDateTime.class))).thenReturn(0);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-4", 55L)));

        assertEquals(62003, exception.getCode());
        assertEquals("coupon already used", exception.getMessage());
        verify(entitlementService, never()).grant(anyLong(), eq(COURSE_ID), anyLong(), anyString());
    }

    /**
     * When the insert loses the idempotency race, the winner is re-read outside the transaction.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void returnsWinnerWhenConcurrentInsertConflicts() {
        when(tradeOrderMapper.selectByUserAndIdempotencyKey(USER_ID, "key-5"))
                .thenReturn(null, order(42L, "LPWIN", OrderStatus.PENDING));
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(0L);
        when(tradeOrderMapper.insertOrder(any(TradeOrder.class)))
                .thenThrow(new DuplicateKeyException("duplicate idempotency key"));
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));

        OrderView view = orderService.create(USER_ID, "web", request("key-5", null));

        assertEquals("LPWIN", view.getOrderNo());
        verify(entitlementService, never()).grant(anyLong(), eq(COURSE_ID), anyLong(), anyString());
    }

    /**
     * A percentage coupon without a rate is rejected as a business error instead of a 500.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectsPercentageCouponWithoutRate() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, COURSE_ID)).thenReturn(0L);
        when(couponMapper.selectUserCoupon(USER_ID, 55L)).thenReturn(userCoupon(55L));
        when(couponMapper.selectCoupon(55L)).thenReturn(percentageCouponWithoutRate());

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-6", 55L)));

        assertEquals(62003, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A negative course price is rejected instead of being clamped to a free order.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectsNegativeCoursePrice() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(-100L, 2));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-7", null)));

        assertEquals(62003, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
        verify(entitlementService, never()).grant(anyLong(), anyLong(), anyLong(), anyString());
    }

    /**
     * An offline course cannot be ordered.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOfflineCourse() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course(9900L, 3));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> orderService.create(USER_ID, "web", request("key-5", null)));

        assertEquals(62003, exception.getCode());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
    }

    /**
     * A buyer only sees the orders the query returns for that user and page bounds are clamped.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void listsBuyerOrdersWithClampedPaging() {
        when(tradeOrderMapper.countByUser(USER_ID, null)).thenReturn(2L);
        when(tradeOrderMapper.selectPageByUser(USER_ID, null, 0L, 100L))
                .thenReturn(List.of(order(42L, "LP1", OrderStatus.PENDING)));
        when(tradeOrderMapper.selectItemsByOrderIds(List.of(42L))).thenReturn(List.of(item()));

        OrderPageView page = orderService.list(USER_ID, null, 0, 500);

        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getPageNum());
        assertEquals(100, page.getPageSize());
        assertEquals(1, page.getRecords().size());
        assertEquals("LP1", page.getRecords().get(0).getOrderNo());
    }

    /**
     * The order list returns every line of a combined order, not only the first one.
     *
     * @author Caspian
     * @since 0.22.0
     */
    @Test
    void listsEveryLineOfACombinedOrder() {
        when(tradeOrderMapper.countByUser(USER_ID, null)).thenReturn(1L);
        when(tradeOrderMapper.selectPageByUser(USER_ID, null, 0L, 20L))
                .thenReturn(List.of(order(42L, "LP1", OrderStatus.PENDING)));
        when(tradeOrderMapper.selectItemsByOrderIds(List.of(42L)))
                .thenReturn(List.of(item(7L), item(9L)));

        OrderPageView page = orderService.list(USER_ID, null, 1, 20);

        assertEquals(1, page.getRecords().size());
        assertEquals(2, page.getRecords().get(0).getItems().size());
    }

    /**
     * An empty page skips the row query entirely.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsEmptyPageWhenNothingMatches() {
        when(tradeOrderMapper.countByUser(USER_ID, OrderStatus.PAID)).thenReturn(0L);

        OrderPageView page = orderService.list(USER_ID, OrderStatus.PAID, 2, 20);

        assertEquals(0, page.getRecords().size());
        assertEquals(2, page.getPageNum());
        verify(tradeOrderMapper, never()).selectPageByUser(anyLong(), any(), anyLong(), anyLong());
    }

    /**
     * Detail returns the snapshot lines of an order owned by the caller.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsOwnedOrderDetail() {
        when(tradeOrderMapper.selectById(42L)).thenReturn(order(42L, "LP1", OrderStatus.PENDING));
        when(tradeOrderMapper.selectItems(42L)).thenReturn(List.of(item()));

        OrderDetailView detail = orderService.detail(USER_ID, 42L);

        assertEquals("LP1", detail.getOrderNo());
        assertEquals(1, detail.getItems().size());
        assertEquals(COURSE_ID, detail.getItems().get(0).getCourseId());
    }

    /**
     * Another buyer's order is rejected and never leaks its content.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsForeignOrderDetail() {
        TradeOrder foreign = order(42L, "LP1", OrderStatus.PENDING);
        foreign.setUserId(9999L);
        when(tradeOrderMapper.selectById(42L)).thenReturn(foreign);

        BizException exception =
                assertThrows(BizException.class, () -> orderService.detail(USER_ID, 42L));

        assertEquals(40301, exception.getCode());
        verify(tradeOrderMapper, never()).selectItems(42L);
    }

    /**
     * Cancelling a pending order closes the order and its pending payment attempt.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void cancelsPendingOrderAndClosesPayment() {
        when(tradeOrderMapper.selectById(42L)).thenReturn(order(42L, "LP1", OrderStatus.PENDING));
        when(tradeOrderMapper.markCancelled(eq(42L), eq("changed mind"), any(LocalDateTime.class)))
                .thenReturn(1);

        CancelOrderResult result = orderService.cancel(USER_ID, 42L, "  changed mind  ");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(paymentMapper).closePendingByOrderId(42L);
    }

    /**
     * Cancelling twice is idempotent; a paid order cannot be cancelled.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void cancelIsIdempotentButRejectsPaidOrders() {
        when(tradeOrderMapper.selectById(42L)).thenReturn(order(42L, "LP1", OrderStatus.CANCELLED));
        when(tradeOrderMapper.selectById(43L)).thenReturn(order(43L, "LP2", OrderStatus.PAID));

        assertEquals(OrderStatus.CANCELLED, orderService.cancel(USER_ID, 42L, null).getStatus());
        BizException exception =
                assertThrows(BizException.class, () -> orderService.cancel(USER_ID, 43L, null));

        assertEquals(62001, exception.getCode());
        verify(tradeOrderMapper, never()).markCancelled(anyLong(), any(), any(LocalDateTime.class));
        verify(paymentMapper, never()).closePendingByOrderId(anyLong());
    }

    private CreateOrderRequest request(String idempotencyKey, Long couponId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCourseId(COURSE_ID);
        request.setIdempotencyKey(idempotencyKey);
        request.setCouponId(couponId);
        return request;
    }

    private CreateOrderRequest combinedRequest(String idempotencyKey, List<Long> courseIds) {
        return combinedRequest(idempotencyKey, courseIds, null);
    }

    private CreateOrderRequest combinedRequest(
            String idempotencyKey, List<Long> courseIds, Long couponId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCourseIds(courseIds);
        request.setIdempotencyKey(idempotencyKey);
        request.setCouponId(couponId);
        return request;
    }

    private void stubInsertedOrder(long orderId) {
        when(tradeOrderMapper.insertOrder(any(TradeOrder.class)))
                .thenAnswer(
                        invocation -> {
                            TradeOrder order = invocation.getArgument(0);
                            order.setId(orderId);
                            return 1;
                        });
    }

    private CourseSnapshot course(long priceFen, int status) {
        return courseWithId(COURSE_ID, priceFen, status);
    }

    private CourseSnapshot courseWithId(long courseId, long priceFen, int status) {
        CourseSnapshot snapshot = new CourseSnapshot();
        snapshot.setCourseId(courseId);
        snapshot.setTitle("Course " + courseId);
        snapshot.setCoverUrl("cover-" + courseId);
        snapshot.setPrice(priceFen);
        snapshot.setStatus(status);
        snapshot.setTeacherId(3L);
        snapshot.setLessonCount(4L);
        return snapshot;
    }

    private TradeOrder order(long id, String orderNo, int status) {
        TradeOrder order = new TradeOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(USER_ID);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setDiscountAmount(new BigDecimal("0.00"));
        order.setPayAmount(new BigDecimal("99.00"));
        order.setStatus(status);
        order.setChannel(PaymentChannel.ALIPAY);
        return order;
    }

    private TradeOrderItem item() {
        return item(COURSE_ID);
    }

    private TradeOrderItem item(long courseId) {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(42L);
        item.setCourseId(courseId);
        item.setCourseTitle("Course " + courseId);
        item.setPrice(new BigDecimal("99.00"));
        return item;
    }

    private UserCoupon userCoupon(long userCouponId) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setId(userCouponId);
        userCoupon.setCouponId(userCouponId);
        userCoupon.setUserId(USER_ID);
        userCoupon.setStatus(0);
        return userCoupon;
    }

    private Coupon percentageCouponWithoutRate() {
        Coupon coupon = coupon();
        coupon.setType(2);
        coupon.setDiscountAmount(null);
        coupon.setDiscountRate(null);
        return coupon;
    }

    private Coupon coupon() {
        Coupon coupon = new Coupon();
        coupon.setId(55L);
        coupon.setType(1);
        coupon.setThresholdAmount(new BigDecimal("0.00"));
        coupon.setDiscountAmount(new BigDecimal("10.00"));
        coupon.setStartAt(LocalDateTime.now().minusDays(1));
        coupon.setEndAt(LocalDateTime.now().plusDays(1));
        coupon.setStatus(1);
        return coupon;
    }
}
