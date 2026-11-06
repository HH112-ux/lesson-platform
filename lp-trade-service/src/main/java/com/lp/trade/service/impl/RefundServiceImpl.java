package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.dto.ApplyRefundRequest;
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
import com.lp.trade.service.RefundService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link RefundService}.
 *
 * <p>The channel refund is synchronous because the buyer needs a definite answer and money moves
 * once per refund number. The entitlement revocation is a cross-service side effect, so it goes
 * through the existing outbox: a learn-service outage delays the revocation but never fails the
 * refund, and the retry, backoff and dead letter behaviour is the same as for a purchase grant.
 *
 * <p>Everything runs in one transaction. The locking read on {@code refund_request} serializes
 * concurrent calls for the same order, and {@code uk_order} is the final guard, so only one call
 * reaches the channel. If the process dies after the channel accepted the refund but before commit,
 * a retry reuses the same refund number and the channel returns the original refund instead of
 * moving money twice.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundServiceImpl.class);

    private static final int REASON_MAX_LENGTH = 200;

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final PaymentGateway paymentGateway;

    private final EntitlementService entitlementService;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order and order line persistence
     * @param paymentMapper successful payment lookup
     * @param refundRequestMapper refund persistence
     * @param paymentGateway channel adapter
     * @param entitlementService outbox backed entitlement operations
     * @author Caspian
     * @since 0.1.0
     */
    public RefundServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            RefundRequestMapper refundRequestMapper,
            PaymentGateway paymentGateway,
            EntitlementService entitlementService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.paymentGateway = paymentGateway;
        this.entitlementService = entitlementService;
    }

    /**
     * Refunds one paid order of the current buyer.
     *
     * @param userId buyer id
     * @param orderId order primary key
     * @param request optional amount and reason
     * @return successful refund
     * @throws BizException when the order is absent, not owned, not paid, or the amount differs
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundView refund(long userId, long orderId, ApplyRefundRequest request) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        RefundRequest existing = refundRequestMapper.selectByOrderIdForUpdate(orderId);
        if (existing != null && Objects.equals(existing.getStatus(), RefundStatus.SUCCESS)) {
            return view(order, existing);
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.PAID)) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE);
        }
        BigDecimal amount = request == null ? null : request.getAmount();
        if (amount == null) {
            amount = order.getPayAmount();
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "amount must be positive");
        }
        if (amount.compareTo(order.getPayAmount()) != 0) {
            throw new BizException(ErrorCode.REFUND_AMOUNT_INVALID);
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(orderId);
        if (payment == null || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE, "no successful payment found");
        }
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(orderId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "order has no line");
        }

        RefundRequest refund =
                existing == null ? createRefund(order, payment, amount, request) : existing;
        String channelRefundNo = paymentGateway.refund(payment, refund.getRefundNo(), amount);
        LocalDateTime now = LocalDateTime.now();
        if (refundRequestMapper.markSuccess(refund.getId(), channelRefundNo, now) == 0) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE, "refund is no longer pending");
        }
        if (tradeOrderMapper.markRefunded(orderId, now) == 0) {
            throw new BizException(ErrorCode.ORDER_NOT_REFUNDABLE, "order is no longer paid");
        }
        entitlementService.revoke(userId, item.getCourseId(), orderId, order.getOrderNo());
        LOG.info(
                "refund succeeded: order={}, refund={}, amount={}",
                order.getOrderNo(),
                refund.getRefundNo(),
                amount);
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setChannelRefundNo(channelRefundNo);
        refund.setRefundedAt(now);
        return view(order, refund);
    }

    private RefundRequest createRefund(
            TradeOrder order,
            PaymentRecord payment,
            BigDecimal amount,
            ApplyRefundRequest request) {
        RefundRequest refund = new RefundRequest();
        refund.setRefundNo("RF" + order.getOrderNo());
        refund.setOrderId(order.getId());
        refund.setPaymentId(payment.getId());
        refund.setUserId(order.getUserId());
        refund.setChannel(order.getChannel());
        refund.setAmount(amount);
        refund.setReason(truncate(request == null ? null : request.getReason()));
        refund.setStatus(RefundStatus.PENDING);
        refundRequestMapper.insert(refund);
        return refund;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, REASON_MAX_LENGTH);
    }

    private static RefundView view(TradeOrder order, RefundRequest refund) {
        return new RefundView(
                refund.getRefundNo(),
                order.getId(),
                order.getOrderNo(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getRefundedAt());
    }
}
