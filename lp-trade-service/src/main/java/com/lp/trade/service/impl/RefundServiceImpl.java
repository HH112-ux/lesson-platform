package com.lp.trade.service.impl;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.money.Money;
import com.lp.trade.client.LearnProgressClient;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.ChannelRefundStatusView;
import com.lp.trade.dto.CourseProgressSummary;
import com.lp.trade.dto.RefundAdminPageView;
import com.lp.trade.dto.RefundAdminView;
import com.lp.trade.dto.RefundView;
import com.lp.trade.entity.AdminRefundRow;
import com.lp.trade.entity.AuditOutbox;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.AuditOutboxMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.service.RefundService;
import com.lp.trade.service.RefundSettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link RefundService}.
 *
 * <p>Applying never moves money. The buyer may apply within seven days of payment and while the
 * course progress is at most twenty percent; outside that window the call fails with {@code 62007}
 * and no row is created. An admitted request waits for operations, and only an approval calls the
 * channel. The order stays paid while the request is pending, moves to refunding during the channel
 * call and becomes refunded only after the channel confirms.
 *
 * <p>A channel rejection is deterministic: the request is stored as failed, the order returns to
 * paid and the buyer may apply again. A timeout, reset connection or unreadable response leaves the
 * channel outcome unknown; the request is parked as unknown and the order stays refunding, so the
 * buyer cannot apply again before operations reconcile the channel bill.
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

    private static final String REJECTED_PREFIX = "channel rejected: ";

    private static final String UNKNOWN_PREFIX = "result unknown: ";

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final PaymentGateway paymentGateway;

    private final RefundSettlementService settlementService;

    private final LearnProgressClient learnProgressClient;

    private final TransactionTemplate transactionTemplate;

    private final AuditOutboxMapper auditOutboxMapper;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order and order line persistence
     * @param paymentMapper successful payment lookup
     * @param refundRequestMapper refund persistence
     * @param paymentGateway channel adapter
     * @param settlementService shared settlement gate for the review and reconciliation paths
     * @param learnProgressClient internal course progress lookup for admission
     * @param transactionTemplate explicit transaction boundary for the channel call
     * @param auditOutboxMapper refund audit intents written in the decision transaction
     * @author Caspian
     * @since 0.1.0
     */
    public RefundServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            RefundRequestMapper refundRequestMapper,
            PaymentGateway paymentGateway,
            RefundSettlementService settlementService,
            LearnProgressClient learnProgressClient,
            TransactionTemplate transactionTemplate,
            AuditOutboxMapper auditOutboxMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.paymentGateway = paymentGateway;
        this.settlementService = settlementService;
        this.learnProgressClient = learnProgressClient;
        this.transactionTemplate = transactionTemplate;
        this.auditOutboxMapper = auditOutboxMapper;
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
    public RefundView apply(long userId, long orderId, ApplyRefundRequest request) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
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

        RefundRequest refund;
        try {
            refund = createRefund(order, payment, amount, reason);
        } catch (DuplicateKeyException exception) {
            // Another request won the unique index race; return its row instead of a 500.
            RefundRequest concurrent = refundRequestMapper.selectActiveByOrderId(orderId);
            if (concurrent == null) {
                throw exception;
            }
            LOG.info(
                    "refund application raced: order={}, refund={}",
                    order.getOrderNo(),
                    concurrent.getRefundNo());
            return view(order, concurrent);
        }
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
     * Reads one refund back from the channel without changing any local state.
     *
     * <p>It stores no finding, retries nothing and settles nothing, so an operator can inspect a
     * stuck refund without altering the books. A missing record and a failed query are reported as
     * unknown instead of a {@code 404} or an empty success.
     *
     * @param refundId refund primary key
     * @return channel snapshot plus the local reconciliation bookkeeping
     * @throws BizException when the refund itself is absent
     * @author Caspian
     * @since 0.9.0
     */
    @Override
    public ChannelRefundStatusView channelStatus(long refundId) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        PaymentRecord payment =
                refund.getPaymentId() == null
                        ? null
                        : paymentMapper.selectById(refund.getPaymentId());
        if (payment == null || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)) {
            return channelView(
                    refund,
                    ChannelRefundStatusView.UNAVAILABLE,
                    null,
                    null,
                    null,
                    "payment is missing or not successful");
        }
        LocalDateTime queriedAt = LocalDateTime.now();
        ChannelRefundState state;
        try {
            state = paymentGateway.refundQuery(payment, refund.getRefundNo());
        } catch (RuntimeException exception) {
            String reason =
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
            return channelView(
                    refund, ChannelRefundStatusView.QUERY_FAILED, queriedAt, null, null, reason);
        }
        Long amountFen = state.refundAmount() == null ? null : Money.toFen(state.refundAmount());
        String status = state.refundStatus();
        if (status == null) {
            return channelView(
                    refund,
                    ChannelRefundStatusView.NOT_FOUND,
                    queriedAt,
                    null,
                    amountFen,
                    "channel has no refund under this request number");
        }
        if (state.succeeded()) {
            return channelView(
                    refund, ChannelRefundStatusView.CONFIRMED, queriedAt, status, amountFen, null);
        }
        return channelView(
                refund,
                ChannelRefundStatusView.OTHER,
                queriedAt,
                status,
                amountFen,
                "channel status does not confirm the refund");
    }

    private static ChannelRefundStatusView channelView(
            RefundRequest refund,
            String outcome,
            LocalDateTime queriedAt,
            String channelRefundStatus,
            Long channelRefundAmountFen,
            String queryMessage) {
        return new ChannelRefundStatusView(
                refund.getId() == null ? 0L : refund.getId(),
                outcome,
                channelRefundStatus,
                channelRefundAmountFen,
                refund.getChannelRefundNo(),
                queriedAt,
                queryMessage,
                refund.getReconcileFinding() == null
                        ? RefundReconcileFinding.NONE
                        : refund.getReconcileFinding(),
                refund.getReconcileCheckedAt(),
                refund.getReconcileAttempts() == null ? 0 : refund.getReconcileAttempts());
    }

    /**
     * Records an operations decision and, when approved, calls the channel.
     *
     * <p>The pending request is claimed with {@code WHERE status = 0} inside its own committed
     * transaction, so the in-progress status is visible to later queries before the channel is
     * contacted. A rejection requires a reason and leaves the order paid. An explicit channel
     * rejection is stored as failed and returns the order to paid; an unknown outcome, such as a
     * timeout or an unreadable response, is stored as unknown and keeps the order refunding.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason required rejection reason
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @param traceId request trace id carried by the audit intent
     * @return refund state after the decision
     * @throws BizException when the refund is absent or the rejection reason is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefundView review(
            long refundId,
            boolean approved,
            String rejectReason,
            long reviewerId,
            int operatorRole,
            String traceId) {
        if (!OperatorRole.isValid(operatorRole)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "operatorRole is invalid");
        }
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
            // The status change and the audit intent commit together; the relay closes the gap to
            // lp_cms.
            transactionTemplate.executeWithoutResult(
                    status -> {
                        refundRequestMapper.markRejected(refundId, reason, reviewerId, now);
                        auditOutboxMapper.insert(
                                auditIntent(
                                        refundId,
                                        reviewerId,
                                        operatorRole,
                                        traceId,
                                        AuditAction.REFUND_REJECT,
                                        RefundStatus.REJECTED,
                                        reason,
                                        now));
                    });
            LOG.info("refund rejected: refund={}, reviewer={}", refund.getRefundNo(), reviewerId);
            return view(order, refundRequestMapper.selectById(refundId));
        }
        Boolean claimed =
                transactionTemplate.execute(
                        status -> {
                            if (refundRequestMapper.markApproved(refundId, reviewerId, now) == 0) {
                                return false;
                            }
                            auditOutboxMapper.insert(
                                    auditIntent(
                                            refundId,
                                            reviewerId,
                                            operatorRole,
                                            traceId,
                                            AuditAction.REFUND_APPROVE,
                                            RefundStatus.APPROVED,
                                            null,
                                            now));
                            return true;
                        });
        if (!Boolean.TRUE.equals(claimed)) {
            return view(order, refundRequestMapper.selectById(refundId));
        }
        return settleApproved(refundId, reviewerId, order);
    }

    private static AuditOutbox auditIntent(
            long refundId,
            long reviewerId,
            int operatorRole,
            String traceId,
            int actionType,
            int toStatus,
            String remark,
            LocalDateTime occurredAt) {
        AuditOutbox outbox = new AuditOutbox();
        outbox.setDedupeKey(
                "refund-review:"
                        + refundId
                        + ":"
                        + occurredAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        outbox.setOperatorId(reviewerId);
        outbox.setOperatorRole(operatorRole);
        outbox.setActionType(actionType);
        outbox.setObjectType(AuditObject.REFUND);
        outbox.setObjectId(refundId);
        outbox.setFromStatus(RefundStatus.PENDING_REVIEW);
        outbox.setToStatus(toStatus);
        outbox.setRemark(remark);
        outbox.setTraceId(traceId);
        outbox.setOccurredAt(occurredAt);
        return outbox;
    }

    /**
     * Reads one page of refunds for the operations review list.
     *
     * @param status status filter, {@code null} for all statuses
     * @param pageNum one based page number
     * @param pageSize page size capped at 100
     * @return refund page
     * @throws BizException when the status filter is outside {@code 0..5}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RefundAdminPageView list(Integer status, int pageNum, int pageSize) {
        if (status != null
                && (status < RefundStatus.PENDING_REVIEW || status > RefundStatus.UNKNOWN)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status must be between 0 and 5");
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
            return fail(refundId, null, "order not found");
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        if (!Objects.equals(order.getStatus(), OrderStatus.PAID)
                || payment == null
                || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)
                || item == null) {
            return fail(refundId, order, "order is no longer refundable");
        }
        Boolean claimed =
                transactionTemplate.execute(
                        status -> tradeOrderMapper.markRefunding(order.getId()) > 0);
        if (!Boolean.TRUE.equals(claimed)) {
            return fail(refundId, order, "order is no longer paid");
        }
        String channelRefundNo;
        try {
            channelRefundNo =
                    paymentGateway.refund(payment, refund.getRefundNo(), refund.getAmount());
        } catch (BizException exception) {
            // The channel answered with a rejection, so the money did not move. Fail the request
            // and return the order to paid, which lets the buyer apply again.
            String reason = failureReason(REJECTED_PREFIX, exception);
            transactionTemplate.executeWithoutResult(
                    status -> {
                        refundRequestMapper.markFailed(refundId, reason);
                        tradeOrderMapper.markPaidAfterRefundFailure(order.getId());
                    });
            LOG.warn(
                    "refund rejected by channel: order={}, refund={}, reason={}",
                    order.getOrderNo(),
                    refund.getRefundNo(),
                    reason);
            return view(order, refundRequestMapper.selectById(refundId));
        } catch (RuntimeException exception) {
            // Timeout, reset connection or unreadable response: the channel may already have moved
            // the money, so keep the order refunding and park the request for reconciliation.
            String reason = failureReason(UNKNOWN_PREFIX, exception);
            transactionTemplate.executeWithoutResult(
                    status -> refundRequestMapper.markUnknown(refundId, reason));
            LOG.warn(
                    "refund channel outcome unknown: order={}, refund={}, reason={}",
                    order.getOrderNo(),
                    refund.getRefundNo(),
                    reason);
            return view(order, refundRequestMapper.selectById(refundId));
        }
        LocalDateTime now = LocalDateTime.now();
        // The channel is satisfied. Record that finding first, because the shared settlement gate
        // refuses a refund whose stored finding is not CHANNEL_DONE.
        transactionTemplate.executeWithoutResult(
                status -> refundRequestMapper.markChannelDone(refundId, now));
        int settled =
                settlementService.settle(
                        refundId,
                        Money.toFen(refund.getAmount()),
                        channelRefundNo,
                        "review:" + reviewerId);
        if (settled == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "refund is no longer approved");
        }
        LOG.info(
                "refund succeeded: order={}, refund={}, reviewer={}",
                order.getOrderNo(),
                refund.getRefundNo(),
                reviewerId);
        return view(order, refundRequestMapper.selectById(refundId));
    }

    private RefundView fail(long refundId, TradeOrder order, String reason) {
        transactionTemplate.executeWithoutResult(
                status -> refundRequestMapper.markFailed(refundId, reason));
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

    private static String failureReason(String prefix, RuntimeException exception) {
        String message = exception.getMessage();
        String detail =
                message == null || message.isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getClass().getSimpleName() + ": " + message;
        String reason = prefix + detail;
        if (reason.length() <= REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, REASON_MAX_LENGTH);
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
