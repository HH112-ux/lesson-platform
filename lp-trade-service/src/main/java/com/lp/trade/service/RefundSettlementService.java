package com.lp.trade.service;

import com.lp.common.money.Money;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Settles one refund whose channel outcome is already confirmed, in a single transaction.
 *
 * <p>The review path and the reconciliation job both call {@link #settle} after the channel
 * confirms, so the refund row, the order, the payment and the revoke intent commit or roll back
 * together. The channel call never runs here, so no database transaction is held while waiting on
 * the network.
 *
 * <p>The caller is not trusted. The row is reloaded and re-checked, and the conditional update
 * repeats the same predicates, so a broken caller, a job retry and an operator action cannot move
 * money twice. A rejected call returns {@code 0} without throwing and without writing, which keeps
 * the normal retry path out of the error log.
 *
 * @author Caspian
 * @since 0.8.0
 */
@Service
public class RefundSettlementService {

    private static final Logger LOG = LoggerFactory.getLogger(RefundSettlementService.class);

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final RefundRequestMapper refundRequestMapper;

    private final EntitlementService entitlementService;

    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order and order line persistence
     * @param paymentMapper successful payment lookup
     * @param refundRequestMapper refund persistence and the conditional settlement update
     * @param entitlementService outbox backed revoke intent, written in the same transaction
     * @param transactionTemplate explicit transaction boundary around the settlement writes
     * @author Caspian
     * @since 0.8.0
     */
    public RefundSettlementService(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            RefundRequestMapper refundRequestMapper,
            EntitlementService entitlementService,
            TransactionTemplate transactionTemplate) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.entitlementService = entitlementService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Moves one channel-confirmed refund to refunded and revokes access in the same transaction.
     *
     * <p>The refund must still be approved or unknown, its last finding must be {@code
     * CHANNEL_DONE}, and the passed amount must equal the stored amount in fen. Any failed check
     * returns {@code 0} without writing, which makes a repeated call a no-op instead of an error.
     *
     * @param refundId refund primary key
     * @param amountFen refund amount in fen, as confirmed by the channel
     * @param channelRefundNo channel refund key, {@code null} when the caller only read the channel
     * @param settledBy settlement source label, {@code review:<userId>} or {@code job}
     * @return {@code 1} when the refund moved to refunded, {@code 0} when any gate rejected it
     * @author Caspian
     * @since 0.8.0
     */
    public int settle(long refundId, long amountFen, String channelRefundNo, String settledBy) {
        return transactionTemplate.execute(
                status -> doSettle(refundId, amountFen, channelRefundNo, settledBy));
    }

    private int doSettle(long refundId, long amountFen, String channelRefundNo, String settledBy) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (!isSettleable(refund, amountFen)) {
            return 0;
        }
        TradeOrder order = tradeOrderMapper.selectById(refund.getOrderId());
        List<TradeOrderItem> items = tradeOrderMapper.selectItems(refund.getOrderId());
        if (order == null || items.isEmpty()) {
            LOG.warn("refund settlement skipped, order or line missing: refund={}", refundId);
            return 0;
        }
        if (items.stream().anyMatch(item -> item.getCourseId() == null)) {
            LOG.warn("refund settlement skipped, order line has no course: refund={}", refundId);
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int moved =
                refundRequestMapper.markSuccess(
                        refundId, channelRefundNo, Money.toYuan(amountFen), settledBy, now);
        if (moved == 0) {
            return 0;
        }
        if (tradeOrderMapper.markRefunded(order.getId(), now) == 0) {
            LOG.warn("order did not move to refunded: order={}", order.getOrderNo());
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        if (payment == null || paymentMapper.markRefunded(payment.getId()) == 0) {
            LOG.warn("payment did not move to refunded: order={}", order.getOrderNo());
        }
        for (TradeOrderItem item : items) {
            entitlementService.revoke(
                    order.getUserId(), item.getCourseId(), order.getId(), order.getOrderNo());
        }
        return 1;
    }

    private static boolean isSettleable(RefundRequest refund, long amountFen) {
        return refund != null
                && (Objects.equals(refund.getStatus(), RefundStatus.APPROVED)
                        || Objects.equals(refund.getStatus(), RefundStatus.UNKNOWN))
                && Objects.equals(refund.getReconcileFinding(), RefundReconcileFinding.CHANNEL_DONE)
                && refund.getAmount() != null
                && Money.toFen(refund.getAmount()) == amountFen;
    }
}
