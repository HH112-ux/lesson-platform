package com.lp.trade.service;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.ChannelTradeState;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Converges local order state with the state of the trade at the payment channel.
 *
 * <p>Owns the channel-first close used by the timeout scan: the trade is closed at the channel
 * before the order is closed locally, so a buyer can never pay an order the platform already
 * closed. A channel answer the platform did not expect is either converged through the shared
 * callback apply path or recorded in {@code payment_notify_anomaly}; nothing falls through to an
 * unclassified error.
 *
 * @author Caspian
 * @since 0.5.0
 */
@Service
public class PaymentChannelSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentChannelSyncService.class);

    private final PaymentGateway paymentGateway;

    private final PaymentService paymentService;

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final PaymentAnomalyRecorder anomalyRecorder;

    /**
     * Creates the service with its channel and persistence dependencies.
     *
     * @param paymentGateway channel close and query port
     * @param paymentService shared payment apply path
     * @param tradeOrderMapper order persistence
     * @param paymentMapper payment persistence
     * @param anomalyRecorder records deviations that need manual review
     * @author Caspian
     * @since 0.5.0
     */
    public PaymentChannelSyncService(
            PaymentGateway paymentGateway,
            PaymentService paymentService,
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            PaymentAnomalyRecorder anomalyRecorder) {
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.anomalyRecorder = anomalyRecorder;
    }

    /**
     * Closes one expired order at the channel before the caller closes it locally.
     *
     * <p>Branches:
     *
     * <ul>
     *   <li>No pending attempt, or no channel configured: returns {@code true} and the caller
     *       closes locally exactly as before.
     *   <li>Channel confirms the trade is gone or closed: returns {@code true}.
     *   <li>Channel reports a payment: it is applied through {@link
     *       PaymentService#applyConfirmedPayment}, which also records amount and state deviations;
     *       returns {@code false} so a paid order is never closed.
     *   <li>Channel calls the trade closed while the order is paid: records an anomaly for manual
     *       review and returns {@code false}.
     *   <li>Close and the follow-up query both fail, or the trade is still open: throws {@code
     *       62020}, the order stays pending and the next scan retries it.
     * </ul>
     *
     * @param order expired order picked by the timeout scan
     * @return {@code true} when the caller must close the order locally
     * @throws BizException with {@code 62020} when the channel close cannot be confirmed
     * @author Caspian
     * @since 0.5.0
     */
    public boolean prepareClose(TradeOrder order) {
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return true;
        }
        boolean closed;
        try {
            closed = paymentGateway.close(payment);
        } catch (BizException exception) {
            if (exception.getCode() == ErrorCode.PAYMENT_NOT_CONFIGURED.getCode()) {
                return true;
            }
            closed = false;
        } catch (RuntimeException exception) {
            closed = false;
        }
        if (closed) {
            return confirmNoChannelPayment(order, payment);
        }
        ChannelTradeState state;
        try {
            state = paymentGateway.query(payment);
        } catch (RuntimeException exception) {
            throw closeFailed(order, "channel close and query both failed");
        }
        if (state.paid()) {
            convergeChannelPayment(order, payment, state);
            return false;
        }
        if (state.absentOrClosed()) {
            return confirmNoChannelPayment(order, payment);
        }
        throw closeFailed(order, "channel trade is still open");
    }

    private BizException closeFailed(TradeOrder order, String detail) {
        return new BizException(
                ErrorCode.PAYMENT_CHANNEL_CLOSE_FAILED, detail + ": " + order.getOrderNo());
    }

    private void convergeChannelPayment(
            TradeOrder order, PaymentRecord payment, ChannelTradeState state) {
        if (state.channelTradeNo() == null
                || state.channelTradeNo().isBlank()
                || state.amount() == null) {
            LOG.error(
                    "channel reports a payment without trade number or amount: orderNo={}",
                    order.getOrderNo());
            return;
        }
        if (paymentMapper.selectByChannelTradeNo(payment.getChannel(), state.channelTradeNo())
                != null) {
            LOG.info(
                    "channel payment already recorded, order stays paid: orderNo={}, tradeNo={}",
                    order.getOrderNo(),
                    state.channelTradeNo());
            return;
        }
        try {
            paymentService.applyConfirmedPayment(
                    notification(
                            order,
                            payment,
                            state.channelTradeNo(),
                            state.amount(),
                            state.tradeStatus()),
                    order,
                    payment);
        } catch (RuntimeException exception) {
            LOG.error(
                    "failed to apply a channel payment: orderNo={}, tradeNo={}",
                    order.getOrderNo(),
                    state.channelTradeNo(),
                    exception);
        }
    }

    private boolean confirmNoChannelPayment(TradeOrder order, PaymentRecord payment) {
        TradeOrder current = tradeOrderMapper.selectById(order.getId());
        if (current == null || current.getStatus() != OrderStatus.PAID) {
            return true;
        }
        PaymentRecord latest = paymentMapper.selectById(payment.getId());
        String tradeNo = latest == null ? null : latest.getChannelTradeNo();
        if (tradeNo == null || tradeNo.isBlank()) {
            LOG.error(
                    "channel reports no payment but the order is paid and no trade number exists:"
                            + " orderNo={}, orderStatus={}, paymentId={}, channel={}",
                    order.getOrderNo(),
                    current.getStatus(),
                    payment.getId(),
                    payment.getChannel());
            return false;
        }
        anomalyRecorder.record(
                notification(
                        order,
                        payment,
                        tradeNo,
                        payment.getAmount(),
                        ChannelTradeState.TRADE_CLOSED),
                payment.getAmount(),
                PaymentAnomalyRecorder.ORDER_NOT_PAYABLE,
                "channel closed the trade but the order is paid, manual review required");
        return false;
    }

    private PaymentNotification notification(
            TradeOrder order,
            PaymentRecord payment,
            String tradeNo,
            BigDecimal amount,
            String tradeStatus) {
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(payment.getChannel());
        notification.setOrderNo(order.getOrderNo());
        notification.setChannelTradeNo(tradeNo);
        notification.setAmount(amount);
        notification.setTradeStatus(tradeStatus);
        return notification;
    }
}
