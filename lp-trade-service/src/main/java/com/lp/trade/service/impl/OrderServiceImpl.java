package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
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
import com.lp.trade.service.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link OrderService} implementation.
 *
 * <p>Order creation is a single transaction: the order, its line and the coupon consumption either
 * all commit or all roll back. A zero payable amount completes the order immediately and grants
 * ownership without touching any payment channel.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final int STATUS_PUBLISHED = 2;

    private static final int PAY_TIMEOUT_MINUTES = 30;

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private static final int MAX_ORDER_NO_RANDOM = 10000;

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TradeOrderMapper tradeOrderMapper;

    private final CouponMapper couponMapper;

    private final CourseInternalClient courseInternalClient;

    private final EntitlementService entitlementService;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order persistence
     * @param couponMapper coupon persistence
     * @param courseInternalClient course-service reader
     * @param entitlementService ownership port
     * @author Caspian
     * @since 0.1.0
     */
    public OrderServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            CouponMapper couponMapper,
            CourseInternalClient courseInternalClient,
            EntitlementService entitlementService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.couponMapper = couponMapper;
        this.courseInternalClient = courseInternalClient;
        this.entitlementService = entitlementService;
    }

    /**
     * Creates an order, or replays the order already created with the same idempotency key.
     *
     * @param userId authenticated buyer id
     * @param source trusted request source
     * @param request course, idempotency key and optional coupon
     * @return created or replayed order
     * @throws BizException when validation or any business rule fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderView create(long userId, String source, CreateOrderRequest request) {
        int channel = resolveChannel(source);
        long courseId = requireCourseId(request);
        String idempotencyKey = requireIdempotencyKey(request);

        TradeOrder existing =
                tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return viewOf(existing);
        }

        CourseSnapshot course = courseInternalClient.course(courseId);
        if (course.getStatus() != STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE);
        }
        if (tradeOrderMapper.countOwned(userId, courseId) > 0) {
            throw new BizException(ErrorCode.COURSE_ALREADY_OWNED);
        }

        BigDecimal total = BigDecimal.valueOf(course.getPrice(), 2);
        CouponSelection selection = selectCoupon(userId, request.getCouponId(), total);
        BigDecimal discount = selection == null ? ZERO : selection.discount;
        BigDecimal payAmount = total.subtract(discount).max(ZERO);

        LocalDateTime now = LocalDateTime.now();
        boolean completed = payAmount.compareTo(ZERO) == 0;
        TradeOrder order =
                buildOrder(
                        userId,
                        idempotencyKey,
                        channel,
                        courseId,
                        total,
                        discount,
                        payAmount,
                        request.getCouponId(),
                        completed,
                        now);
        try {
            tradeOrderMapper.insertOrder(order);
        } catch (DuplicateKeyException exception) {
            // A concurrent request with the same key won; return its order instead of charging.
            TradeOrder winner =
                    tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
            if (winner != null) {
                return viewOf(winner);
            }
            throw exception;
        }

        insertItem(order.getId(), course, total, discount, payAmount);
        consumeCoupon(selection, order.getId(), now);
        if (completed) {
            entitlementService.grant(userId, courseId, order.getId(), order.getOrderNo());
        }
        return buildView(order, courseId);
    }

    private TradeOrder buildOrder(
            long userId,
            String idempotencyKey,
            int channel,
            long courseId,
            BigDecimal total,
            BigDecimal discount,
            BigDecimal payAmount,
            Long couponId,
            boolean completed,
            LocalDateTime now) {
        TradeOrder order = new TradeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setDiscountAmount(discount);
        order.setPayAmount(payAmount);
        order.setStatus(completed ? OrderStatus.PAID : OrderStatus.PENDING);
        order.setCouponId(couponId);
        order.setChannel(channel);
        order.setIdempotencyKey(idempotencyKey);
        if (completed) {
            order.setPaidAt(now);
        } else {
            order.setPayExpireAt(now.plusMinutes(PAY_TIMEOUT_MINUTES));
        }
        return order;
    }

    private void insertItem(
            Long orderId,
            CourseSnapshot course,
            BigDecimal total,
            BigDecimal discount,
            BigDecimal payAmount) {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(orderId);
        item.setCourseId(course.getCourseId());
        item.setOwnerTeacherId(course.getTeacherId());
        item.setCourseTitle(course.getTitle());
        item.setPrice(total);
        item.setDiscountAmount(discount);
        item.setActualAmount(payAmount);
        tradeOrderMapper.insertItem(item);
    }

    private void consumeCoupon(CouponSelection selection, Long orderId, LocalDateTime now) {
        if (selection == null) {
            return;
        }
        int updated = couponMapper.markUsed(selection.userCouponId, orderId, now);
        if (updated == 0) {
            // The conditional update lost the race; failing here rolls back the whole order.
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "coupon already used");
        }
    }

    private CouponSelection selectCoupon(long userId, Long couponId, BigDecimal total) {
        if (couponId == null) {
            return null;
        }
        UserCoupon userCoupon = couponMapper.selectUserCoupon(userId, couponId);
        if (userCoupon == null || userCoupon.getStatus() != 0) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "coupon is unavailable");
        }
        Coupon coupon = couponMapper.selectCoupon(couponId);
        LocalDateTime now = LocalDateTime.now();
        if (coupon == null
                || coupon.getStatus() != 1
                || now.isBefore(coupon.getStartAt())
                || now.isAfter(coupon.getEndAt())) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "coupon is unavailable");
        }
        if (total.compareTo(coupon.getThresholdAmount()) < 0) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "coupon threshold not reached");
        }
        BigDecimal discount = computeDiscount(coupon, total);
        return new CouponSelection(userCoupon.getId(), discount);
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal total) {
        BigDecimal discount;
        if (coupon.getType() == 1) {
            discount = coupon.getDiscountAmount() == null ? ZERO : coupon.getDiscountAmount();
        } else if (coupon.getType() == 2) {
            discount =
                    total.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()))
                            .setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "unsupported coupon type");
        }
        if (discount.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return discount.min(total);
    }

    private OrderView viewOf(TradeOrder order) {
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        long courseId = item == null ? 0L : item.getCourseId();
        return buildView(order, courseId);
    }

    private OrderView buildView(TradeOrder order, long courseId) {
        return new OrderView(
                order.getOrderNo(),
                order.getStatus(),
                order.getChannel(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayAmount(),
                courseId,
                order.getPayExpireAt(),
                order.getPaidAt());
    }

    private int resolveChannel(String source) {
        if (IdentityHeaders.SOURCE_WEB.equals(source)) {
            return PaymentChannel.ALIPAY;
        }
        if (IdentityHeaders.SOURCE_MINIAPP.equals(source)) {
            return PaymentChannel.WXPAY;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "request source must be web or miniapp");
    }

    private long requireCourseId(CreateOrderRequest request) {
        if (request == null || request.getCourseId() == null || request.getCourseId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseId is required");
        }
        return request.getCourseId();
    }

    private String requireIdempotencyKey(CreateOrderRequest request) {
        String key = request.getIdempotencyKey();
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "idempotencyKey is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "idempotencyKey is too long");
        }
        return trimmed;
    }

    private String generateOrderNo() {
        int random = ThreadLocalRandom.current().nextInt(MAX_ORDER_NO_RANDOM);
        return "LP" + LocalDateTime.now().format(ORDER_NO_TIME) + String.format("%04d", random);
    }

    private static final class CouponSelection {

        private final long userCouponId;

        private final BigDecimal discount;

        private CouponSelection(long userCouponId, BigDecimal discount) {
            this.userCouponId = userCouponId;
            this.discount = discount;
        }
    }
}
