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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.Coupon;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.entity.UserCoupon;
import com.lp.trade.mapper.CouponMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks private OrderServiceImpl orderService;

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
        assertEquals(0, new BigDecimal("99.00").compareTo(view.getPayAmount()));
        assertNotNull(view.getPayExpireAt());
        assertNull(view.getPaidAt());
        assertTrue(view.getOrderNo().startsWith("LP"));
        verify(tradeOrderMapper).insertItem(any(TradeOrderItem.class));
        verify(entitlementService, never()).grant(anyLong(), eq(COURSE_ID), anyLong(), anyString());
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
        when(tradeOrderMapper.selectFirstItem(42L)).thenReturn(item());

        OrderView view = orderService.create(USER_ID, "web", request("key-1", null));

        assertEquals("LP20260101000000001", view.getOrderNo());
        assertEquals(COURSE_ID, view.getCourseId());
        verify(tradeOrderMapper, never()).insertOrder(any(TradeOrder.class));
        verify(courseInternalClient, never()).course(COURSE_ID);
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

    private CreateOrderRequest request(String idempotencyKey, Long couponId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCourseId(COURSE_ID);
        request.setIdempotencyKey(idempotencyKey);
        request.setCouponId(couponId);
        return request;
    }

    private CourseSnapshot course(long priceFen, int status) {
        CourseSnapshot snapshot = new CourseSnapshot();
        snapshot.setCourseId(COURSE_ID);
        snapshot.setTitle("Course " + COURSE_ID);
        snapshot.setCoverUrl("cover-" + COURSE_ID);
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
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(42L);
        item.setCourseId(COURSE_ID);
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
