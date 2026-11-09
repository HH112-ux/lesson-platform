package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.money.Money;
import com.lp.trade.client.LearnProgressClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.CourseProgressSummary;
import com.lp.trade.dto.RefundAdminPageView;
import com.lp.trade.dto.RefundAdminView;
import com.lp.trade.dto.RefundView;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.AdminRefundRow;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.service.RefundService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link RefundService}.
 *
 * <p>Applying never moves money. The buyer may apply within seven days of payment and while the
 * course progress is at most twenty percent; outside that window the call fails with {@code 62007}
 * and no row is created. An admitted request waits for operations, and only an approval calls the
 * channel. The order stays paid while the request is pending, moves to refunding during the channel
 * call and becomes refunded only after the channel confirms.
 *
 * <p>All state transitions use conditional updates, so a duplicated application, review or channel
 * callback returns the stored result instead of moving money twice. The generated unique key on
 * {@code refund_request.active_order_id} is the final guard for one in-progress refund per order.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundServiceImpl.class);

    private static final int ADMISSION_DAYS = 7;

    private static final int MAX_PROGRESS_PERCENT = 20;

    private static final int REASON_MIN_LENGTH = 1;

    private static final int REASON_MAX_LENGTH = 300;

    private static final int MAX_PAGE_SIZE = 100;

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final PaymentGateway paymentGateway;

    private final EntitlementService entitlementService;

    private final LearnProgressClient learnProgressClient;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order and order line persistence
     * @param paymentMapper successful payment lookup
     * @param refundRequestMapper refund persistence
     * @param paymentGateway channel adapter
     * @param entitlementService outbox backed entitlement operations
     * @param learnProgressClient internal course progress lookup for admission
     * @author Caspian
     * @since 0.1.0
     */
    public RefundServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            RefundRequestMapper refundRequestMapper,
            PaymentGateway paymentGateway,
            EntitlementService entitlementService,
            LearnProgressClient learnProgressClient) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.paymentGateway = paymentGateway;
        this.entitlementService = entitlementService;
        this.learnProgressClient = learnProgressClient;
    }

    /**
     * Applies for a full refund without calling the channel.
     *
     * @param userId buyer id
     * @param orderId order primary key
     * @param request reason and optional amount
     * @return pending, approved or already refunded request
     * @throws BizException when the order is absent, not owned, not paid, the amount differs, the
     *     reason is invalid, or the admission rules are not met
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundView apply(long userId, long orderId, ApplyRefundRequest request) {
        TradeOrder order = tradeOrderMapper.selectByIdForUpdate(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        RefundRequest existing = refundRequestMapper.selectActiveByOrderId(orderId);
        if (existing != null) {
            return view(order, existing);
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.PAID)) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE);
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(orderId);
        if (payment == null || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE, "no successful payment found");
        }
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(orderId);
        if (item == null || item.getCourseId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "order has no line");
        }
        String reason = validateReason(request == null ? null : request.getReason());
        BigDecimal amount = resolveAmount(order, request);
        checkAdmission(order, userId, item.getCourseId());

        RefundRequest refund = createRefund(order, payment, amount, reason);
        LOG.info(
                "refund application accepted: order={}, refund={}, amount={}",
                order.getOrderNo(),
                refund.getRefundNo(),
                amount);
        return view(order, refund);
    }

    /**
     * Returns one refund of the current buyer.
     *
     * @param userId buyer id
     * @param refundId refund primary key
     * @return refund state
     * @throws BizException when the refund is absent or owned by another buyer
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefundView get(long userId, long refundId) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(refund.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        TradeOrder order = tradeOrderMapper.selectById(refund.getOrderId());
        return view(order, refund);
    }

    /**
     * Records an operations decision and, when approved, calls the channel.
     *
     * <p>The pending request is claimed with {@code WHERE status = 0}; a duplicated review returns
     * the stored decision. A rejection requires a reason and leaves the order paid. A channel
     * failure stores its reason, returns the order to paid and keeps the entitlement, so operations
     * can handle it manually.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason required rejection reason
     * @param reviewerId operations user id
     * @return refund state after the decision
     * @throws BizException when the refund is absent or the rejection reason is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundView review(
            long refundId, boolean approved, String rejectReason, long reviewerId) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        TradeOrder order = tradeOrderMapper.selectById(refund.getOrderId());
        if (!Objects.equals(refund.getStatus(), RefundStatus.PENDING_REVIEW)) {
            return view(order, refund);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!approved) {
            String reason = validateReason(rejectReason);
            if (refundRequestMapper.markRejected(refundId, reason, reviewerId, now) == 0) {
                return view(order, refundRequestMapper.selectById(refundId));
            }
            LOG.info("refund rejected: refund={}, reviewer={}", refund.getRefundNo(), reviewerId);
            return view(order, refundRequestMapper.selectById(refundId));
        }
        if (refundRequestMapper.markApproved(refundId, reviewerId, now) == 0) {
            return view(order, refundRequestMapper.selectById(refundId));
        }
        return settleApproved(refundId, reviewerId, order);
    }

    /**
     * Reads one page of refunds for the operations review list.
     *
     * @param status status filter, {@code null} for all statuses
     * @param pageNum one based page number
     * @param pageSize page size capped at 100
     * @return refund page
     * @throws BizException when the status filter is outside {@code 0..4}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefundAdminPageView list(Integer status, int pageNum, int pageSize) {
        if (status != null
                && (status < RefundStatus.PENDING_REVIEW || status > RefundStatus.FAILED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status must be between 0 and 4");
        }
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = refundRequestMapper.countForAdmin(status);
        List<RefundAdminView> records =
                refundRequestMapper
                        .selectAdminPage(status, (safePageNum - 1) * safePageSize, safePageSize)
                        .stream()
                        .map(RefundServiceImpl::adminView)
                        .toList();
        return new RefundAdminPageView(records, total, safePageNum, safePageSize);
    }

    private RefundView settleApproved(long refundId, long reviewerId, TradeOrder order) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (order == null) {
            refundRequestMapper.markFailed(refundId, "order not found");
            return view(null, refundRequestMapper.selectById(refundId));
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        if (!Objects.equals(order.getStatus(), OrderStatus.PAID)
                || payment == null
                || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)
                || item == null) {
            refundRequestMapper.markFailed(refundId, "order is no longer refundable");
            return view(order, refundRequestMapper.selectById(refundId));
        }
        if (tradeOrderMapper.markRefunding(order.getId()) == 0) {
            refundRequestMapper.markFailed(refundId, "order is no longer paid");
            return view(order, refundRequestMapper.selectById(refundId));
        }
        String channelRefundNo;
        try {
            channelRefundNo =
                    paymentGateway.refund(payment, refund.getRefundNo(), refund.getAmount());
        } catch (BizException exception) {
            refundRequestMapper.markFailed(refundId, failureReason(exception));
            tradeOrderMapper.markPaidAfterRefundFailure(order.getId());
            LOG.warn(
                    "refund channel failed: order={}, refund={}, reason={}",
                    order.getOrderNo(),
                    refund.getRefundNo(),
                    exception.getMessage());
            return view(order, refundRequestMapper.selectById(refundId));
        }
        LocalDateTime now = LocalDateTime.now();
        if (refundRequestMapper.markSuccess(refundId, channelRefundNo, now) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "refund is no longer approved");
        }
        if (tradeOrderMapper.markRefunded(order.getId(), now) == 0) {
            LOG.warn("order did not move to refunded: order={}", order.getOrderNo());
        }
        entitlementService.revoke(
                order.getUserId(), item.getCourseId(), order.getId(), order.getOrderNo());
        LOG.info(
                "refund succeeded: order={}, refund={}, reviewer={}",
                order.getOrderNo(),
                refund.getRefundNo(),
                reviewerId);
        return view(order, refundRequestMapper.selectById(refundId));
    }

    private void checkAdmission(TradeOrder order, long userId, long courseId) {
        LocalDateTime paidAt = order.getPaidAt();
        if (paidAt == null || LocalDateTime.now().isAfter(paidAt.plusDays(ADMISSION_DAYS))) {
            throw new BizException(
                    ErrorCode.REFUND_ADMISSION_NOT_MET, "refund must be applied within seven days");
        }
        CourseProgressSummary progress = learnProgressClient.progress(userId, courseId);
        if (progress.progressPercent() > MAX_PROGRESS_PERCENT) {
            throw new BizException(
                    ErrorCode.REFUND_ADMISSION_NOT_MET,
                    "refund is not available after twenty percent progress");
        }
    }

    private BigDecimal resolveAmount(TradeOrder order, ApplyRefundRequest request) {
        Long amountFen = request == null ? null : request.getAmountFen();
        if (amountFen == null) {
            amountFen = Money.toFen(order.getPayAmount());
        }
        if (!Money.isStorable(amountFen) || amountFen <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "amount must be positive");
        }
        BigDecimal amount = Money.toYuan(amountFen);
        if (amount.compareTo(order.getPayAmount()) != 0) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_INVALID);
        }
        return amount;
    }

    private static String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason is required");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < REASON_MIN_LENGTH || trimmed.length() > REASON_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason must be 1 to 300 characters");
        }
        return trimmed;
    }

    private RefundRequest createRefund(
            TradeOrder order, PaymentRecord payment, BigDecimal amount, String reason) {
        int previous = refundRequestMapper.countByOrderId(order.getId());
        String refundNo =
                previous == 0
                        ? "RF" + order.getOrderNo()
                        : "RF" + order.getOrderNo() + "-" + (previous + 1);
        RefundRequest refund = new RefundRequest();
        refund.setRefundNo(refundNo);
        refund.setOrderId(order.getId());
        refund.setPaymentId(payment.getId());
        refund.setUserId(order.getUserId());
        refund.setChannel(payment.getChannel());
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING_REVIEW);
        refundRequestMapper.insert(refund);
        return refund;
    }

    private static String failureReason(BizException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "channel refund failed";
        }
        return message.length() <= REASON_MAX_LENGTH
                ? message
                : message.substring(0, REASON_MAX_LENGTH);
    }

    private static RefundView view(TradeOrder order, RefundRequest refund) {
        return new RefundView(
                refund.getId(),
                refund.getRefundNo(),
                refund.getOrderId(),
                order == null ? null : order.getOrderNo(),
                refund.getChannel() == null ? 0 : refund.getChannel(),
                Money.toFen(refund.getAmount()),
                refund.getStatus() == null ? RefundStatus.PENDING_REVIEW : refund.getStatus(),
                refund.getReason(),
                refund.getReviewReason(),
                refund.getFailReason(),
                refund.getRefundedAt());
    }

    private static RefundAdminView adminView(AdminRefundRow row) {
        return new RefundAdminView(
                row.getId(),
                row.getRefundNo(),
                row.getOrderNo(),
                row.getUserId(),
                row.getChannel() == null ? 0 : row.getChannel(),
                Money.toFen(row.getAmount()),
                row.getReason(),
                row.getStatus() == null ? RefundStatus.PENDING_REVIEW : row.getStatus(),
                row.getReviewReason(),
                row.getFailReason(),
                row.getReviewedBy(),
                row.getReviewedAt(),
                row.getRefundedAt(),
                row.getCreatedAt());
    }
}
