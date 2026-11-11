package com.lp.trade.job;

import com.lp.common.exception.BizException;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.mapper.CouponMapper;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.service.PaymentChannelSyncService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Closes pending orders whose payment deadline has passed.
 *
 * <p>Order close is a conditional transition from status 0 to 3. A payment callback that already
 * committed first leaves the order paid, so the close updates no row and the scan must leave the
 * payment record and the occupied coupon untouched. When the close wins the race, the callback
 * fails its own conditional update, retries later and is recorded as paid after close for refund
 * compensation. Either way an order is never both paid and closed.
 *
 * <p>Before an order is closed locally, its trade is closed at the channel. A close the channel
 * cannot confirm leaves the order pending and the next scan retries it, so the platform never
 * closes an order whose trade can still collect money.
 *
 * <p>Each order closes in its own transaction, so one failure neither rolls back earlier orders nor
 * stops the scan; a repeated scan finds no pending row and therefore stays silent.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Component
public class OrderTimeoutJob {

    private static final Logger LOG = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private static final int BATCH_SIZE = 200;

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final CouponMapper couponMapper;

    private final TransactionTemplate transactionTemplate;

    private final PaymentChannelSyncService paymentChannelSync;

    /**
     * Creates the job with its persistence and transaction dependencies.
     *
     * @param tradeOrderMapper order persistence
     * @param paymentMapper payment persistence
     * @param couponMapper coupon persistence
     * @param transactionTemplate one transaction per order
     * @param paymentChannelSync channel-first close before the local close
     * @author Caspian
     * @since 0.2.0
     */
    public OrderTimeoutJob(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            CouponMapper couponMapper,
            TransactionTemplate transactionTemplate,
            PaymentChannelSyncService paymentChannelSync) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.couponMapper = couponMapper;
        this.transactionTemplate = transactionTemplate;
        this.paymentChannelSync = paymentChannelSync;
    }

    /**
     * Closes one batch of expired orders; never throws so the scheduler keeps running.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Scheduled(
            fixedDelayString = "${lp.trade.order-timeout-scan-delay-ms:60000}",
            initialDelayString = "${lp.trade.order-timeout-initial-delay-ms:30000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        List<TradeOrder> expired;
        try {
            expired = tradeOrderMapper.selectExpiredPending(now, BATCH_SIZE);
        } catch (RuntimeException exception) {
            LOG.warn("order timeout scan failed: {}", exception.getMessage());
            return;
        }
        for (TradeOrder order : expired) {
            try {
                if (!paymentChannelSync.prepareClose(order)) {
                    continue;
                }
                closeOne(order.getId(), now);
            } catch (BizException exception) {
                LOG.error(
                        "order timeout close failed: orderId={}, code={}, reason={}",
                        order.getId(),
                        exception.getCode(),
                        exception.getMessage());
            } catch (RuntimeException exception) {
                LOG.error(
                        "order timeout close failed: orderId={}, reason={}",
                        order.getId(),
                        exception.getMessage());
            }
        }
    }

    /**
     * Closes one expired order together with its pending payments and coupon.
     *
     * <p>Returns {@code false} without side effects when the order is no longer pending, which is
     * exactly what a payment callback that already committed produces.
     *
     * @param orderId order primary key
     * @param now close time
     * @return {@code true} when this call closed the order
     * @author Caspian
     * @since 0.2.0
     */
    public boolean closeOne(long orderId, LocalDateTime now) {
        return Boolean.TRUE.equals(
                transactionTemplate.execute(status -> closeInTransaction(orderId, now)));
    }

    private boolean closeInTransaction(long orderId, LocalDateTime now) {
        if (tradeOrderMapper.markTimedOut(orderId, now) == 0) {
            return false;
        }
        paymentMapper.closePendingByOrderId(orderId);
        couponMapper.releaseByOrderId(orderId);
        return true;
    }
}
