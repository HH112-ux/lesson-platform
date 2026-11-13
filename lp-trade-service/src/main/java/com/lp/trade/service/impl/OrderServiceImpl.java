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
import com.lp.trade.service.AccountDeletionGuard;
import com.lp.trade.service.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    /** Maximum number of distinct courses accepted in one combined order. */
    private static final int MAX_COMBINED_COURSES = 20;

    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TradeOrderMapper tradeOrderMapper;

    private final CouponMapper couponMapper;

    private final CourseInternalClient courseInternalClient;

    private final EntitlementService entitlementService;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final TransactionTemplate transactionTemplate;

    private final AccountDeletionGuard accountDeletionGuard;

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
     * @param accountDeletionGuard cooling-off gate applied before a new order is created
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
            TransactionTemplate transactionTemplate,
            AccountDeletionGuard accountDeletionGuard) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.couponMapper = couponMapper;
        this.courseInternalClient = courseInternalClient;
        this.entitlementService = entitlementService;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.transactionTemplate = transactionTemplate;
        this.accountDeletionGuard = accountDeletionGuard;
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
        CourseSelection selection = requireCourseSelection(request);
        String idempotencyKey = requireIdempotencyKey(request);

        TradeOrder existing =
                tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return replay(existing, selection, request.getCouponId());
        }

        // Only a new order is gated: replaying one created before the cooling-off period keeps the
        // idempotent answer a client already holds.
        accountDeletionGuard.assertNotCoolingOff(userId);

        try {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status ->
                                    createInTransaction(
                                            userId,
                                            channel,
                                            selection.courseIds(),
                                            idempotencyKey,
                                            request)));
        } catch (DuplicateKeyException exception) {
            // The conflicting insert waited for the winner to commit before failing, so a read
            // outside the rolled back transaction sees it; the old in-snapshot re-read did not.
            TradeOrder winner =
                    tradeOrderMapper.selectByUserAndIdempotencyKey(userId, idempotencyKey);
            if (winner != null) {
                return replay(winner, selection, request.getCouponId());
            }
            throw exception;
        }
    }

    /**
     * Creates the order, its lines and the coupon consumption inside one transaction.
     *
     * <p>A combined order is only created without a coupon in this release; coupon support for
     * combined orders is added separately. Each line stores its own price and, for combined orders,
     * its own payable amount because no discount is allocated here.
     *
     * @param userId authenticated buyer id
     * @param channel resolved payment channel
     * @param courseIds normalized, deduplicated and sorted course ids
     * @param idempotencyKey client supplied key, unique per buyer
     * @param request original request
     * @return created order
     * @author Caspian
     * @since 0.2.0
     */
    private OrderView createInTransaction(
            long userId,
            int channel,
            List<Long> courseIds,
            String idempotencyKey,
            CreateOrderRequest request) {
        boolean combined = courseIds.size() > 1;
        if (combined && request.getCouponId() != null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "coupon is not supported for combined orders");
        }

        List<CourseSnapshot> courses = new ArrayList<>(courseIds.size());
        BigDecimal total = ZERO;
        for (Long courseId : courseIds) {
            CourseSnapshot course = courseInternalClient.course(courseId);
            validateCourse(course);
            if (tradeOrderMapper.countOwned(userId, courseId) > 0) {
                throw new BizException(ErrorCode.COURSE_ALREADY_OWNED);
            }
            courses.add(course);
            total = total.add(BigDecimal.valueOf(course.getPrice(), 2));
        }
        if (!Money.isStorable(total.movePointRight(2).longValueExact())) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "course price is invalid");
        }

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
                        total,
                        discount,
                        payAmount,
                        request.getCouponId(),
                        completed,
                        now);
        // Let a duplicate key escape the transaction so the caller can re-read outside its
        // snapshot.
        tradeOrderMapper.insertOrder(order);

        List<LineAmounts> lines = new ArrayList<>(courses.size());
        for (CourseSnapshot course : courses) {
            if (combined) {
                BigDecimal lineAmount = BigDecimal.valueOf(course.getPrice(), 2);
                lines.add(new LineAmounts(lineAmount, ZERO, lineAmount));
            } else {
                lines.add(new LineAmounts(total, discount, payAmount));
            }
        }
        requireReconciled(lines, total, discount, payAmount);
        for (int index = 0; index < courses.size(); index++) {
            insertItem(order.getId(), courses.get(index), lines.get(index));
        }
        consumeCoupon(selection, order.getId(), now);
        if (completed) {
            for (CourseSnapshot course : courses) {
                entitlementService.grant(
                        userId, course.getCourseId(), order.getId(), order.getOrderNo());
            }
        }
        return buildView(order, courses.stream().map(this::toItemView).toList());
    }

    private void validateCourse(CourseSnapshot course) {
        if (course.getStatus() != STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE);
        }
        if (!Money.isStorable(course.getPrice())) {
            // A negative or oversized price would be clamped to a free order or overflow the
            // column.
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE, "course price is invalid");
        }
    }

    private TradeOrder buildOrder(
            long userId,
            String idempotencyKey,
            int channel,
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

    private void insertItem(Long orderId, CourseSnapshot course, LineAmounts amounts) {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(orderId);
        item.setCourseId(course.getCourseId());
        item.setOwnerTeacherId(course.getTeacherId());
        item.setCourseTitle(course.getTitle());
        item.setPrice(amounts.price());
        item.setDiscountAmount(amounts.discount());
        item.setActualAmount(amounts.pay());
        tradeOrderMapper.insertItem(item);
    }

    /**
     * Fails the transaction when the line amounts do not add up to the order amounts.
     *
     * <p>Refunds and reconciliation work from the order-level amounts, while reports and support
     * read the lines, so the two must always agree. A coupon is only accepted on a single course
     * order, therefore no discount may be spread over several lines; the check keeps that rule true
     * even if the line construction changes later.
     *
     * @param lines amounts about to be stored on the order lines
     * @param total order total in yuan
     * @param discount order discount in yuan
     * @param payAmount order payable amount in yuan
     * @throws IllegalStateException when any line sum differs from its order amount
     * @author Caspian
     * @since 0.22.0
     */
    private void requireReconciled(
            List<LineAmounts> lines, BigDecimal total, BigDecimal discount, BigDecimal payAmount) {
        long lineTotal = 0;
        long lineDiscount = 0;
        long linePay = 0;
        for (LineAmounts line : lines) {
            lineTotal += Money.toFen(line.price());
            lineDiscount += Money.toFen(line.discount());
            linePay += Money.toFen(line.pay());
        }
        if (lineTotal != Money.toFen(total)
                || lineDiscount != Money.toFen(discount)
                || linePay != Money.toFen(payAmount)) {
            throw new IllegalStateException("order lines do not reconcile with the order amounts");
        }
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
     * <p>The stored course set is deduplicated and sorted, so request order is irrelevant. A
     * combined order additionally binds the server computed total: replaying after a price change
     * is a different request and is rejected with {@code 40901}. Legacy single course requests keep
     * the previous behavior and do not compare amounts.
     *
     * @param order stored order owning the key
     * @param selection normalized current request
     * @param couponId coupon id from the current request, may be {@code null}
     * @return stored order view
     * @throws BizException with {@code 40901} when the request payload differs
     * @author Caspian
     * @since 0.3.0
     */
    private OrderView replay(TradeOrder order, CourseSelection selection, Long couponId) {
        List<TradeOrderItem> items = tradeOrderMapper.selectItems(order.getId());
        List<Long> storedCourseIds =
                items.stream().map(TradeOrderItem::getCourseId).distinct().sorted().toList();
        if (!storedCourseIds.equals(selection.courseIds())
                || !Objects.equals(order.getCouponId(), couponId)) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, "idempotency key reused with a different request");
        }
        if (!selection.legacy()) {
            BigDecimal currentTotal =
                    selection.courseIds().stream()
                            .map(courseInternalClient::course)
                            .map(course -> BigDecimal.valueOf(course.getPrice(), 2))
                            .reduce(ZERO, BigDecimal::add);
            String requestedDigest =
                    IdempotencyDigests.order(
                            selection.courseIds(), couponId, Money.toFen(currentTotal));
            String storedDigest =
                    IdempotencyDigests.order(
                            storedCourseIds,
                            order.getCouponId(),
                            Money.toFen(order.getTotalAmount()));
            if (!requestedDigest.equals(storedDigest)) {
                throw new BizException(
                        ErrorCode.STATE_CONFLICT,
                        "idempotency key reused after the order amount changed");
            }
        }
        return buildView(order, items.stream().map(this::toItemView).toList());
    }

    /**
     * Resolves and normalizes the courses of a request.
     *
     * <p>The legacy {@code courseId} and the combined {@code courseIds} are mutually exclusive: a
     * request carrying both is rejected instead of guessing which one the caller meant. Combined
     * ids are validated, deduplicated and sorted so the idempotency digest is order independent.
     *
     * @param request order creation request
     * @return normalized course selection
     * @throws BizException with {@code 40001} when the selection is missing, invalid or too large
     * @author Caspian
     * @since 0.22.0
     */
    private CourseSelection requireCourseSelection(CreateOrderRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body is required");
        }
        Long legacyCourseId = request.getCourseId();
        List<Long> requestedCourseIds = request.getCourseIds();
        if (legacyCourseId != null && requestedCourseIds != null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "courseId and courseIds must not be sent together");
        }
        if (legacyCourseId != null) {
            if (legacyCourseId <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "courseId is required");
            }
            return new CourseSelection(List.of(legacyCourseId), true);
        }
        if (requestedCourseIds == null || requestedCourseIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseId or courseIds is required");
        }
        for (Long courseId : requestedCourseIds) {
            if (courseId == null || courseId <= 0) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID, "courseIds contains an invalid course id");
            }
        }
        List<Long> normalized = requestedCourseIds.stream().distinct().sorted().toList();
        if (normalized.size() > MAX_COMBINED_COURSES) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "courseIds must contain at most " + MAX_COMBINED_COURSES + " distinct courses");
        }
        return new CourseSelection(normalized, false);
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
        Map<Long, List<TradeOrderItem>> itemsByOrder = new HashMap<>();
        if (!rows.isEmpty()) {
            // One query for every line on the page instead of one query per row.
            for (TradeOrderItem item :
                    tradeOrderMapper.selectItemsByOrderIds(
                            rows.stream().map(TradeOrder::getId).toList())) {
                itemsByOrder.computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(item);
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
                                                itemsByOrder
                                                        .getOrDefault(order.getId(), List.of())
                                                        .stream()
                                                        .map(this::toItemView)
                                                        .toList(),
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

    private OrderItemView toItemView(CourseSnapshot course) {
        return new OrderItemView(course.getCourseId(), course.getTitle(), course.getPrice());
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

    /**
     * Builds the order projection, keeping the first course id for single course clients.
     *
     * @param order persisted order
     * @param items every line of the order
     * @return order view
     * @author Caspian
     * @since 0.1.0
     */
    private OrderView buildView(TradeOrder order, List<OrderItemView> items) {
        long firstCourseId = items.isEmpty() ? 0L : items.get(0).getCourseId();
        return new OrderView(
                order.getOrderNo(),
                order.getStatus(),
                order.getChannel(),
                Money.toFen(order.getTotalAmount()),
                Money.toFen(order.getDiscountAmount()),
                Money.toFen(order.getPayAmount()),
                firstCourseId,
                items,
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

    /**
     * Normalized course selection of one order creation request.
     *
     * @param courseIds deduplicated and sorted course ids
     * @param legacy whether the request used the legacy single {@code courseId} field
     * @author Caspian
     * @since 0.22.0
     */
    private record CourseSelection(List<Long> courseIds, boolean legacy) {}

    /**
     * Amounts stored on one order line.
     *
     * @param price line price in yuan
     * @param discount line discount in yuan
     * @param pay line payable amount in yuan
     * @author Caspian
     * @since 0.22.0
     */
    private record LineAmounts(BigDecimal price, BigDecimal discount, BigDecimal pay) {}
}
