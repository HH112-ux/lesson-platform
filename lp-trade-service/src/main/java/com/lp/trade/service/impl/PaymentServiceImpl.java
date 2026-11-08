package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.trade.domain.OrderStatus;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.dto.PayView;
import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entitlement.EntitlementService;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.payment.SignatureVerifier;
import com.lp.trade.service.PaymentService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link PaymentService} implementation.
 *
 * <p>Payment initiation never fakes a payable payload: while the gateway is unconfigured, prepay
 * fails with {@code 50205}. Callback handling is transactional and guarded twice, first by the
 * unique channel trade number and then by a conditional status update, so duplicated notifications
 * grant ownership only once.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final int MAX_PAY_NO_RANDOM = 10000;

    private static final DateTimeFormatter PAY_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TradeOrderMapper tradeOrderMapper;

    private final PaymentMapper paymentMapper;

    private final PaymentGateway paymentGateway;

    private final SignatureVerifier signatureVerifier;

    private final EntitlementService entitlementService;

    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the service with its dependencies.
     *
     * @param tradeOrderMapper order persistence
     * @param paymentMapper payment persistence
     * @param paymentGateway channel prepay port
     * @param signatureVerifier callback verification port
     * @param entitlementService ownership port
     * @param transactionTemplate transaction boundary around the attempt lock
     * @author Caspian
     * @since 0.1.0
     */
    public PaymentServiceImpl(
            TradeOrderMapper tradeOrderMapper,
            PaymentMapper paymentMapper,
            PaymentGateway paymentGateway,
            SignatureVerifier signatureVerifier,
            EntitlementService entitlementService,
            TransactionTemplate transactionTemplate) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.paymentGateway = paymentGateway;
        this.signatureVerifier = signatureVerifier;
        this.entitlementService = entitlementService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Creates or reuses a payment attempt and requests a prepay payload.
     *
     * @param userId authenticated buyer id
     * @param source trusted request source
     * @param orderNo business order number
     * @return prepay payload
     * @throws BizException when the order is missing, not payable, expired, or misconfigured
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PayView initiate(long userId, String source, String orderNo) {
        int channel = resolveChannel(source);
        String trimmedOrderNo = requireOrderNo(orderNo);
        PreparedPayment prepared =
                Objects.requireNonNull(
                        transactionTemplate.execute(
                                status -> lockAndPrepare(userId, channel, trimmedOrderNo)));
        if (prepared.expired()) {
            throw new BizException(ErrorCode.ORDER_EXPIRED);
        }
        PrepayResult prepay = paymentGateway.prepay(prepared.payment());
        return new PayView(prepay.getPayNo(), prepay.getChannel(), prepay.getPayParams());
    }

    /**
     * Validates the order under its row lock and returns the attempt to prepay.
     *
     * <p>The order row is locked for the whole read-validate-insert sequence, so a concurrent click
     * waits and then reuses the pending attempt instead of inserting a second one. Prepay is
     * deliberately outside this transaction: holding a row lock during a channel call would block
     * the buyer's next request for as long as the provider takes.
     *
     * @param userId authenticated buyer id
     * @param channel resolved payment channel
     * @param orderNo trimmed business order number
     * @return attempt to prepay, or an expired marker when the order was just closed
     * @author Caspian
     * @since 0.2.0
     */
    private PreparedPayment lockAndPrepare(long userId, int channel, String orderNo) {
        TradeOrder order = tradeOrderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "order not found");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BizException(ErrorCode.TRADE_BIZ_ERROR, "order already paid");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BizException(ErrorCode.TRADE_BIZ_ERROR, "order is not payable");
        }
        if (order.getChannel() != channel) {
            throw new BizException(ErrorCode.TRADE_CHANNEL_SOURCE_MISMATCH);
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.getPayExpireAt() != null && now.isAfter(order.getPayExpireAt())) {
            paymentMapper.closePendingByOrderId(order.getId());
            tradeOrderMapper.markClosed(order.getId(), now);
            return new PreparedPayment(null, true);
        }

        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            payment = newPayment(order, channel);
            paymentMapper.insertPayment(payment);
        }
        return new PreparedPayment(payment, false);
    }

    /**
     * Verifies and applies one callback exactly once.
     *
     * @param channel channel of the callback endpoint
     * @param headers HTTP headers carrying the channel signature
     * @param rawBody raw callback body
     * @param notification parsed callback fields
     * @throws BizException when verification fails, the channel or amount mismatches, or the order
     *     is no longer payable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(
            int channel,
            Map<String, String> headers,
            String rawBody,
            PaymentNotification notification) {
        signatureVerifier.verify(channel, headers, rawBody, notification);
        requireNotification(notification, channel);
        String tradeNo = notification.getChannelTradeNo().trim();
        if (paymentMapper.selectByChannelTradeNo(channel, tradeNo) != null) {
            return;
        }

        TradeOrder order = tradeOrderMapper.selectByOrderNo(notification.getOrderNo().trim());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "order not found");
        }
        if (order.getChannel() != channel) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_MISMATCH);
        }
        PaymentRecord payment = paymentMapper.selectLatestByOrderId(order.getId());
        if (payment == null || payment.getChannel() != channel) {
            throw new BizException(ErrorCode.NOT_FOUND, "payment not found");
        }
        if (payment.getAmount().compareTo(notification.getAmount()) != 0) {
            throw new BizException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        int updated = paymentMapper.markSuccess(payment.getId(), tradeNo, paidAt);
        if (updated == 0) {
            handleNonPendingCallback(order, payment, tradeNo, paidAt);
            return;
        }
        int orderUpdated = tradeOrderMapper.markPaid(order.getId(), paidAt);
        if (orderUpdated == 0) {
            throw new BizException(ErrorCode.TRADE_BIZ_ERROR, "order is not payable");
        }
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        if (item == null) {
            throw new BizException(ErrorCode.TRADE_BIZ_ERROR, "order has no item");
        }
        entitlementService.grant(
                order.getUserId(), item.getCourseId(), order.getId(), order.getOrderNo());
    }

    /**
     * Handles a verified callback that could not move the attempt from pending to success.
     *
     * <p>Only a repeated callback on an already successful attempt is a normal no-op. A closed
     * attempt means the order timeout won the race: the money was still collected, so the attempt
     * is persisted in {@link PaymentStatus#PAID_AFTER_CLOSE} for reconciliation and refund
     * compensation, and an error log is emitted instead of silently dropping the callback. A
     * missing attempt is rejected because there is nothing to reconcile against.
     *
     * @param order order the callback referred to
     * @param payment attempt selected before the conditional update
     * @param tradeNo channel trade number from the notification
     * @param paidAt channel payment time
     * @author Caspian
     * @since 0.2.0
     */
    private void handleNonPendingCallback(
            TradeOrder order, PaymentRecord payment, String tradeNo, LocalDateTime paidAt) {
        PaymentRecord latest = paymentMapper.selectById(payment.getId());
        if (latest == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "payment not found");
        }
        if (latest.getStatus() == PaymentStatus.SUCCESS) {
            // The first callback already granted ownership; the channel retried the notification.
            return;
        }
        if (paymentMapper.markPaidAfterClose(payment.getId(), tradeNo, paidAt) == 0) {
            PaymentRecord current = paymentMapper.selectById(payment.getId());
            if (current != null && current.getStatus() == PaymentStatus.SUCCESS) {
                return;
            }
            throw new BizException(
                    ErrorCode.TRADE_BIZ_ERROR, "payment state changed during callback");
        }
        LOG.error(
                "late payment callback recorded for refund compensation: orderNo={}, payNo={}, "
                        + "channelTradeNo={}, previousStatus={}, amount={}",
                order.getOrderNo(),
                payment.getPayNo(),
                tradeNo,
                latest.getStatus(),
                payment.getAmount());
    }

    /**
     * Outcome of the locked preparation step.
     *
     * @param payment attempt to prepay, {@code null} when the order just expired
     * @param expired whether the expiry check closed the order
     * @author Caspian
     * @since 0.2.0
     */
    private record PreparedPayment(PaymentRecord payment, boolean expired) {}

    private PaymentRecord newPayment(TradeOrder order, int channel) {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(order.getId());
        payment.setPayNo(generatePayNo());
        payment.setChannel(channel);
        payment.setPayScene(
                channel == PaymentChannel.ALIPAY
                        ? PaymentChannel.SCENE_WEB
                        : PaymentChannel.SCENE_MINIAPP);
        payment.setAmount(order.getPayAmount());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private void requireNotification(PaymentNotification notification, int channel) {
        if (notification == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "notification is required");
        }
        if (notification.getChannel() != channel) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_MISMATCH);
        }
        if (notification.getOrderNo() == null || notification.getOrderNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderNo is required");
        }
        if (notification.getChannelTradeNo() == null
                || notification.getChannelTradeNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "channel trade number is required");
        }
        if (notification.getAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "amount is required");
        }
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

    private String requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderNo is required");
        }
        return orderNo.trim();
    }

    private String generatePayNo() {
        int random = ThreadLocalRandom.current().nextInt(MAX_PAY_NO_RANDOM);
        return "PY" + LocalDateTime.now().format(PAY_NO_TIME) + String.format("%04d", random);
    }
}
