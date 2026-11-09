package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.money.Money;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.CancelOrderResult;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderDetailView;
import com.lp.trade.dto.OrderItemView;
import com.lp.trade.dto.OrderPageView;
import com.lp.trade.dto.OrderSummaryView;
import com.lp.trade.dto.OrderView;
import com.lp.trade.dto.RefundSummaryView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.Coupon;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.entity.UserCoupon;
import com.lp.trade.idempotency.IdempotencyDigests;
import com.lp.trade.mapper.CouponMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.service.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    /** Maximum page size accepted by the order list. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum cancel reason length, matching {@code trade_order.cancel_reason}. */
    private static final int MAX_CANCEL_REASON_LENGTH = 200;

    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TradeOrderMapper tradeOrderMapper;

    private final CouponMapper couponMapper;

    private final CourseInternalClient courseInternalClient;

    private final EntitlementService entitlementService;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order persistence
     * @param couponMapper coupon persistence
     * @param courseInternalClient course-service reader
     * @param entitlementService ownership port
     * @param paymentMapper payment persistence used to close attempts on cancel
     * @param refundRequestMapper refund state embedded in order rows
     * @param transactionTemplate transaction boundary around order creation
     * @author Caspian
     * @since 0.1.0
     */
    public OrderServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            CouponMapper couponMapper,
            CourseInternalClient courseInternalClient,
            EntitlementService entitlementService,
            PaymentMapper paymentMapper,
            RefundRequestMapper refundRequestMapper,
            TransactionTemplate transactionTemplate) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.couponMapper = couponMapper;
        this.courseInternalClient = courseInternalClient;
        this.entitlementService = entitlementService;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.transactionTemplate = transactionTemplate;
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
    public OrderView create(long userId, String source, CreateOrderRequest request) {
        int channel = resolveChannel(source);
        long courseId = requireCourseId(request);
        String idempotencyKey = requireIdempotencyKey(request);

        TradeOrder existing =
                tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return replay(existing, courseId, request.getCouponId());
        }

        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status ->
                                    createInTransaction(
                                            userId, channel, courseId, idempotencyKey, request)));
        } catch (DuplicateKeyException exception) {
            // The conflicting insert waited for the winner to commit before failing, so a read
            // outside the rolled back transaction sees it; the old in-snapshot re-read did not.
            TradeOrder winner =
                    tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
            if (winner != null) {
                return replay(winner, courseId, request.getCouponId());
            }
            throw exception;
        }
    }

    /**
     * Creates the order, its line and the coupon consumption inside one transaction.
     *
     * @param userId authenticated buyer id
     * @param channel resolved payment channel
     * @param courseId course primary key
     * @param idempotencyKey client supplied key, unique per buyer
     * @param request original request
     * @return created order
     * @author Caspian
     * @since 0.2.0
     */
    private OrderView createInTransaction(
            long userId,
            int channel,
            long courseId,
            String idempotencyKey,
            CreateOrderRequest request) {
        CourseSnapshot course = courseInternalClient.course(courseId);
        if (course.getStatus() != STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE);
        }
        if (!Money.isStorable(course.getPrice())) {
            // A negative or oversized price would be clamped to a free order or overflow the
            // column.
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "course price is invalid");
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
        // Let a duplicate key escape the transaction so the caller can re-read outside its
        // snapshot.
        tradeOrderMapper.insertOrder(order);

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
        Integer type = coupon.getType();
        BigDecimal discount;
        if (type == null) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "unsupported coupon type");
        }
        if (type == 1) {
            discount = coupon.getDiscountAmount() == null ? ZERO : coupon.getDiscountAmount();
        } else if (type == 2) {
            BigDecimal rate = coupon.getDiscountRate();
            if (rate == null
                    || rate.compareTo(BigDecimal.ZERO) <= 0
                    || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new BizException(
                        ErrorCode.TRADE_UNAVAILABLE, "coupon discount rate is invalid");
            }
            discount =
                    total.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "unsupported coupon type");
        }
        if (discount.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return discount.min(total);
    }

    /**
     * Returns the stored order when the idempotency key is replayed with the same payload.
     *
     * <p>A key is bound to the course and coupon of the first request. Reusing it for a different
     * payload is rejected instead of returning the first order, so a stale or copied key cannot
     * silently buy another course in the buyer's name.
     *
     * @param order stored order owning the key
     * @param courseId course id from the current request
     * @param couponId coupon id from the current request, may be {@code null}
     * @return stored order view
     * @throws BizException with {@code 40901} when the request payload differs
     * @author Caspian
     * @since 0.3.0
     */
    private OrderView replay(TradeOrder order, long courseId, Long couponId) {
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        long storedCourseId = item == null ? 0L : item.getCourseId();
        if (!requestDigest(storedCourseId, order.getCouponId())
                .equals(requestDigest(courseId, couponId))) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, "idempotency key reused with a different request");
        }
        return buildView(order, storedCourseId);
    }

    /**
     * Builds the stable payload digest an idempotency key is bound to.
     *
     * @param courseId course id
     * @param couponId coupon id, may be {@code null}
     * @return lowercase hex SHA-256 digest
     * @author Caspian
     * @since 0.3.0
     */
    private String requestDigest(long courseId, Long couponId) {
        return IdempotencyDigests.order(courseId, couponId);
    }

    /**
     * Lists one page of the buyer orders, newest first.
     *
     * @param userId authenticated buyer id
     * @param status optional status filter
     * @param pageNum one based page number
     * @param pageSize page size
     * @return order page
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public OrderPageView list(long userId, Integer status, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Integer filter = status != null && status >= 0 ? status : null;
        long total = tradeOrderMapper.countByUser(userId, filter);
        if (total == 0) {
            return new OrderPageView(List.of(), 0, safePage, safeSize);
        }
        List<TradeOrder> rows =
                tradeOrderMapper.selectPageByUser(
                        userId, filter, (long) (safePage - 1) * safeSize, safeSize);
        Map<Long, TradeOrderItem> firstItems = new HashMap<>();
        if (!rows.isEmpty()) {
            // One query for every line on the page instead of one query per row.
            for (TradeOrderItem item :
                    tradeOrderMapper.selectItemsByOrderIds(
                            rows.stream().map(TradeOrder::getId).toList())) {
                firstItems.putIfAbsent(item.getOrderId(), item);
            }
        }
        Map<Long, RefundRequest> latestRefunds = new HashMap<>();
        if (!rows.isEmpty()) {
            for (RefundRequest refund :
                    refundRequestMapper.selectLatestByOrderIds(
                            rows.stream().map(TradeOrder::getId).toList())) {
                latestRefunds.put(refund.getOrderId(), refund);
            }
        }
        List<OrderSummaryView> records =
                rows.stream()
                        .map(
                                order ->
                                        new OrderSummaryView(
                                                order.getId(),
                                                order.getOrderNo(),
                                                order.getStatus(),
                                                order.getChannel(),
                                                Money.toFen(order.getPayAmount()),
                                                order.getCreatedAt(),
                                                List.of(toItemView(firstItems.get(order.getId()))),
                                                refundSummary(latestRefunds.get(order.getId()))))
                        .toList();
        return new OrderPageView(records, total, safePage, safeSize);
    }

    /**
     * Loads one order of the buyer.
     *
     * @param userId authenticated buyer id
     * @param orderId order primary key
     * @return order detail
     * @throws BizException when the order is absent or owned by someone else
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public OrderDetailView detail(long userId, long orderId) {
        TradeOrder order = requireOwnedOrder(userId, orderId);
        List<OrderItemView> items =
                tradeOrderMapper.selectItems(orderId).stream().map(this::toItemView).toList();
        List<RefundRequest> refunds = refundRequestMapper.selectLatestByOrderIds(List.of(orderId));
        return new OrderDetailView(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getChannel(),
                Money.toFen(order.getTotalAmount()),
                Money.toFen(order.getDiscountAmount()),
                Money.toFen(order.getPayAmount()),
                order.getPayExpireAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getCancelReason(),
                items,
                refundSummary(refunds.isEmpty() ? null : refunds.get(0)));
    }

    /**
     * Cancels a pending order and closes its pending payment attempts.
     *
     * @param userId authenticated buyer id
     * @param orderId order primary key
     * @param reason optional buyer supplied reason
     * @return resulting status, always 2
     * @throws BizException when the order is absent, owned by someone else or no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelOrderResult cancel(long userId, long orderId, String reason) {
        TradeOrder order = requireOwnedOrder(userId, orderId);
        int status = order.getStatus();
        if (status == OrderStatus.CANCELLED) {
            return new CancelOrderResult(orderId, OrderStatus.CANCELLED);
        }
        if (status != OrderStatus.PENDING) {
            throw new BizException(
                    ErrorCode.TRADE_BIZ_ERROR, "only a pending order can be cancelled");
        }
        int updated =
                tradeOrderMapper.markCancelled(
                        orderId, normalizeReason(reason), LocalDateTime.now());
        if (updated == 0) {
            // A concurrent cancel or payment won the race; report the resulting state.
            TradeOrder latest = tradeOrderMapper.selectById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.CANCELLED) {
                return new CancelOrderResult(orderId, OrderStatus.CANCELLED);
            }
            throw new BizException(ErrorCode.TRADE_BIZ_ERROR, "order is no longer cancellable");
        }
        paymentMapper.closePendingByOrderId(orderId);
        return new CancelOrderResult(orderId, OrderStatus.CANCELLED);
    }

    /**
     * Loads an order and rejects callers that do not own it.
     *
     * @param userId authenticated buyer id
     * @param orderId order primary key
     * @return order owned by the caller
     * @throws BizException {@code 40401} when absent, {@code 40301} when owned by someone else
     * @author Caspian
     * @since 0.1.0
     */
    private TradeOrder requireOwnedOrder(long userId, long orderId) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (order.getUserId() == null || order.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    /**
     * Trims the cancel reason and caps it at the column length.
     *
     * @param reason raw reason
     * @return normalized reason, {@code null} when blank
     * @author Caspian
     * @since 0.1.0
     */
    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > MAX_CANCEL_REASON_LENGTH
                ? trimmed.substring(0, MAX_CANCEL_REASON_LENGTH)
                : trimmed;
    }

    /**
     * Maps an order line to the response row; an order without lines maps to a zero priced row.
     *
     * @param item stored line
     * @return response row
     * @author Caspian
     * @since 0.1.0
     */
    private OrderItemView toItemView(TradeOrderItem item) {
        if (item == null) {
            return new OrderItemView(0L, "", 0L);
        }
        return new OrderItemView(
                item.getCourseId(), item.getCourseTitle(), Money.toFen(item.getPrice()));
    }

    private RefundSummaryView refundSummary(RefundRequest refund) {
        if (refund == null) {
            return null;
        }
        return new RefundSummaryView(
                refund.getId(),
                refund.getRefundNo(),
                refund.getStatus() == null ? 0 : refund.getStatus(),
                Money.toFen(refund.getAmount()),
                refund.getReason(),
                refund.getReviewReason(),
                refund.getFailReason(),
                refund.getRefundedAt());
    }

    private OrderView buildView(TradeOrder order, long courseId) {
        return new OrderView(
                order.getOrderNo(),
                order.getStatus(),
                order.getChannel(),
                Money.toFen(order.getTotalAmount()),
                Money.toFen(order.getDiscountAmount()),
                Money.toFen(order.getPayAmount()),
                courseId,
                order.getPayExpireAt(),
                order.getPaidAt());
    }

    private int resolveChannel(String source) {
        if (IdentityHeaders.SOURCE_WEB.equals(source)) {
            return PaymentChannel.ALIPAY;
        }
        if (IdentityHeaders.SOURCE_MINIAPP.equals(source)) {
            throw new BizException(
                    ErrorCode.PAYMENT_CHANNEL_RETIRED,
                    "miniapp payments are no longer supported; purchase on the web");
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
