package com.lp.trade.payment.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.config.AlipayPayProperties;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.ChannelOutcomeUnknownException;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.ChannelTradeState;
import com.lp.trade.payment.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Alipay openapi adapter for web page payments.
 *
 * <p>Only the public key mode with {@code RSA2} is used: requests are signed with the application
 * private key, responses and callbacks are verified with the Alipay public key by the SDK. The page
 * pay response is a self submitting form, so the client must render and submit it instead of
 * redirecting to a URL of its own.
 *
 * <p>The adapter and the local mock channel are mutually exclusive: enabling both leaves two {@link
 * PaymentGateway} beans and aborts startup, which is the intended fail fast for a misconfigured
 * deployment.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Service
@ConditionalOnProperty(prefix = "lp.pay.alipay", name = "enabled", havingValue = "true")
public class AlipayPaymentGateway implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(AlipayPaymentGateway.class);

    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    private static final String CHARSET = "UTF-8";

    private static final String SIGN_TYPE = "RSA2";

    private static final int SUBJECT_MAX_LENGTH = 100;

    /** Alipay sub code of a trade it never created or already removed. */
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final AlipayPayProperties properties;

    private final TradeProperties tradeProperties;

    private final TradeOrderMapper tradeOrderMapper;

    private final AlipayClient client;

    /**
     * Creates the adapter and its SDK client from the verified credentials.
     *
     * @param properties channel settings
     * @param tradeProperties trade settings holding the public web base URL
     * @param tradeOrderMapper order reader used to build channel requests
     * @param credentials verified application credentials
     * @throws AlipayApiException when the SDK rejects the client configuration
     * @author Caspian
     * @since 0.4.0
     */
    public AlipayPaymentGateway(
            AlipayPayProperties properties,
            TradeProperties tradeProperties,
            TradeOrderMapper tradeOrderMapper,
            AlipayCredentials credentials)
            throws AlipayApiException {
        this(properties, tradeProperties, tradeOrderMapper, newClient(properties, credentials));
    }

    /**
     * Creates the adapter around a prepared SDK client so the request mapping can be tested without
     * channel credentials.
     *
     * @param properties channel settings
     * @param tradeProperties trade settings holding the public web base URL
     * @param tradeOrderMapper order reader used to build channel requests
     * @param client SDK client, already configured with the application credentials
     * @author Caspian
     * @since 0.5.0
     */
    AlipayPaymentGateway(
            AlipayPayProperties properties,
            TradeProperties tradeProperties,
            TradeOrderMapper tradeOrderMapper,
            AlipayClient client) {
        this.properties = properties;
        this.tradeProperties = tradeProperties;
        this.tradeOrderMapper = tradeOrderMapper;
        this.client = client;
    }

    private static AlipayClient newClient(
            AlipayPayProperties properties, AlipayCredentials credentials)
            throws AlipayApiException {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(credentials.appId());
        config.setPrivateKey(credentials.appPrivateKey());
        config.setAlipayPublicKey(credentials.alipayPublicKey());
        config.setFormat("json");
        config.setCharset(CHARSET);
        config.setSignType(SIGN_TYPE);
        return new DefaultAlipayClient(config);
    }

    /**
     * Builds the signed page pay form for one payment attempt.
     *
     * <p>The subject and amount are taken from the persisted order, never from the request, and the
     * return URL points at the buyer result page of that order.
     *
     * @param payment pending payment attempt
     * @return payment payload whose {@code paymentHtml} entry holds the self submitting form
     * @throws BizException when the order is missing or the channel refuses the request
     * @author Caspian
     * @since 0.4.0
     */
    @Override
    public PrepayResult prepay(PaymentRecord payment) {
        TradeOrder order = requireOrder(payment);
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(amount(payment.getAmount()));
        model.setSubject(subject(order));
        model.setProductCode(PRODUCT_CODE);
        model.setTimeoutExpress(timeoutExpress(order));
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setReturnUrl(returnUrl(order.getOrderNo()));
        if (StringUtils.hasText(properties.getNotifyUrl())) {
            request.setNotifyUrl(properties.getNotifyUrl());
        }
        try {
            String form = client.pageExecute(request, "POST").getBody();
            if (!StringUtils.hasText(form)) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay returned no form");
            }
            Map<String, String> payParams = new LinkedHashMap<>();
            payParams.put("paymentHtml", form);
            payParams.put("formMethod", "POST");
            payParams.put("payNo", payment.getPayNo());
            payParams.put("channel", Integer.toString(PaymentChannel.ALIPAY));
            LOG.info("alipay page pay form built, payNo={}", payment.getPayNo());
            return new PrepayResult(payment.getPayNo(), PaymentChannel.ALIPAY, payParams);
        } catch (AlipayApiException exception) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay page pay request failed");
        }
    }

    /**
     * Sends one refund to the channel and confirms whether it moved money.
     *
     * <p>The local refund number is sent as {@code out_request_no}, which is Alipay's idempotency
     * key for refunds: replaying the same number returns the first result instead of refunding
     * again. Alipay has no separate refund number, so the same value is returned as the channel
     * reference.
     *
     * <p>The answer is only trusted when the channel states that funds changed and reports exactly
     * the requested amount. Otherwise the refund is read back once through {@link
     * #refundQuery(PaymentRecord, String)}; a read failure, a missing record, a non-success status
     * or a different amount all raise {@link ChannelOutcomeUnknownException}. The refund request is
     * never sent twice, because a duplicate refund is worse than one that waits for operations.
     *
     * @param payment successful payment to refund
     * @param refundNo local refund number, sent as {@code out_request_no}
     * @param amount amount in yuan, never above {@code payment.amount}
     * @return {@code refundNo}, because Alipay has no separate refund number
     * @throws BizException when refunds are disabled, the amount is invalid, or the channel proves
     *     the trade never existed, so no money moved
     * @throws ChannelOutcomeUnknownException when the outcome cannot be confirmed
     * @author Caspian
     * @since 0.4.0
     */
    @Override
    public String refund(PaymentRecord payment, String refundNo, BigDecimal amount) {
        if (!properties.isRefundEnabled()) {
            throw new BizException(ErrorCode.PAYMENT_NOT_CONFIGURED, "alipay refund is disabled");
        }
        TradeOrder order = requireOrder(payment);
        BigDecimal refundAmount = requireRefundableAmount(payment, amount);
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setOutRequestNo(refundNo);
        model.setRefundAmount(amount(refundAmount));
        if (StringUtils.hasText(payment.getChannelTradeNo())) {
            model.setTradeNo(payment.getChannelTradeNo());
        }
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        AlipayTradeRefundResponse response;
        try {
            response = client.execute(request);
        } catch (AlipayApiException exception) {
            throw new ChannelOutcomeUnknownException("alipay refund outcome is unknown", exception);
        }
        if (response == null) {
            throw new ChannelOutcomeUnknownException("alipay returned no refund response");
        }
        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                // The channel never created this trade, so no refund can have run.
                throw new BizException(
                        ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay trade does not exist");
            }
            // ACQ.TRADE_STATUS_ERROR is not a definite rejection: it also covers a trade that was
            // already refunded, where the money did move. Only a proven no-op may be a rejection.
            throw new ChannelOutcomeUnknownException(
                    "alipay refund rejected ambiguously: " + response.getSubCode());
        }
        if (fundsChangedBy(response, refundAmount)) {
            LOG.info(
                    "alipay refund accepted, orderNo={}, refundNo={}, amount={}",
                    order.getOrderNo(),
                    refundNo,
                    refundAmount);
            return refundNo;
        }
        return confirmRefund(payment, order, refundNo, refundAmount);
    }

    /**
     * Reads the refund state for one out request number.
     *
     * <p>The call is read-only. A refund the channel does not report is returned as {@link
     * ChannelRefundState#notFound()}, which the caller must not treat as proof that no refund
     * happened. A transport failure or an unreadable amount raises {@link
     * ChannelOutcomeUnknownException} so no caller can mistake it for a definite answer.
     *
     * @param payment successful payment being refunded
     * @param refundNo out request number previously sent as the channel idempotency key
     * @return channel refund state, never {@code null}
     * @throws BizException when the channel rejects the query
     * @throws ChannelOutcomeUnknownException when the response cannot be read
     * @author Caspian
     * @since 0.6.0
     */
    @Override
    public ChannelRefundState refundQuery(PaymentRecord payment, String refundNo) {
        TradeOrder order = requireOrder(payment);
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(order.getOrderNo());
        if (StringUtils.hasText(payment.getChannelTradeNo())) {
            model.setTradeNo(payment.getChannelTradeNo());
        }
        model.setOutRequestNo(refundNo);
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);
        AlipayTradeFastpayRefundQueryResponse response;
        try {
            response = client.execute(request);
        } catch (AlipayApiException exception) {
            throw new ChannelOutcomeUnknownException(
                    "alipay refund query outcome is unknown", exception);
        }
        if (response == null) {
            throw new ChannelOutcomeUnknownException("alipay returned no refund query response");
        }
        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                LOG.info(
                        "alipay refund does not exist, orderNo={}, refundNo={}",
                        order.getOrderNo(),
                        refundNo);
                return ChannelRefundState.notFound();
            }
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR,
                    "alipay refund query rejected: " + response.getSubCode());
        }
        return new ChannelRefundState(
                response.getRefundStatus(), parseRefundAmount(response.getRefundAmount()));
    }

    /**
     * Queries the channel for the current state of the trade behind one payment attempt.
     *
     * <p>A trade the channel does not know is a normal answer, not an error: it means the buyer
     * never reached the channel, so there is nothing to close or reconcile.
     *
     * @param payment payment attempt to inspect
     * @return channel state, never {@code null}
     * @throws BizException when the request fails or the channel rejects it
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public ChannelTradeState query(PaymentRecord payment) {
        TradeOrder order = requireOrder(payment);
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(order.getOrderNo());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        AlipayTradeQueryResponse response;
        try {
            response = client.execute(request);
        } catch (AlipayApiException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay trade query failed");
        }
        if (response == null) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay returned no trade query response");
        }
        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                LOG.info("alipay trade does not exist, orderNo={}", order.getOrderNo());
                return ChannelTradeState.notFound();
            }
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR,
                    "alipay trade query rejected: " + response.getSubCode());
        }
        if (!StringUtils.hasText(response.getTradeStatus())) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay trade query returned no status");
        }
        if (!StringUtils.hasText(response.getTotalAmount())) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay trade query returned no amount");
        }
        try {
            return new ChannelTradeState(
                    response.getTradeStatus(),
                    response.getTradeNo(),
                    new BigDecimal(response.getTotalAmount()));
        } catch (NumberFormatException exception) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR,
                    "alipay trade query returned an invalid amount");
        }
    }

    /**
     * Closes the trade behind one payment attempt so the buyer can no longer pay it.
     *
     * <p>An unknown trade is a successful close. A trade that already moved on, such as a paid
     * trade, is reported as an upstream error so the caller can query the trade and converge.
     *
     * @param payment pending payment attempt
     * @return {@code true} when no further payment can complete on this trade
     * @throws BizException when the request fails or the channel rejects the close
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public boolean close(PaymentRecord payment) {
        TradeOrder order = requireOrder(payment);
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(order.getOrderNo());
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        AlipayTradeCloseResponse response;
        try {
            response = client.execute(request);
        } catch (AlipayApiException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay trade close failed");
        }
        if (response == null) {
            throw new BizException(
                    ErrorCode.CHANNEL_UPSTREAM_ERROR, "alipay returned no trade close response");
        }
        if (response.isSuccess() || TRADE_NOT_EXIST.equals(response.getSubCode())) {
            LOG.info("alipay trade close accepted, orderNo={}", order.getOrderNo());
            return true;
        }
        throw new BizException(
                ErrorCode.CHANNEL_UPSTREAM_ERROR,
                "alipay trade close rejected: " + response.getSubCode());
    }

    private TradeOrder requireOrder(PaymentRecord payment) {
        TradeOrder order = tradeOrderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "order not found");
        }
        return order;
    }

    private BigDecimal requireRefundableAmount(PaymentRecord payment, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "refund amount must be positive");
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (payment.getAmount() == null || rounded.compareTo(payment.getAmount()) > 0) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "refund amount exceeds the paid amount");
        }
        return rounded;
    }

    private boolean fundsChangedBy(AlipayTradeRefundResponse response, BigDecimal expected) {
        if (!"Y".equals(response.getFundChange())) {
            return false;
        }
        BigDecimal refunded =
                parseChannelAmount(response.getRefundFee(), "alipay refund returned no fee");
        return refunded.compareTo(expected) == 0;
    }

    private String confirmRefund(
            PaymentRecord payment, TradeOrder order, String refundNo, BigDecimal expected) {
        ChannelRefundState state;
        try {
            state = refundQuery(payment, refundNo);
        } catch (RuntimeException exception) {
            // A failed read is not proof that the refund did not run, so it must never become a
            // rejection; the refund request itself is not sent again.
            throw new ChannelOutcomeUnknownException(
                    "alipay refund confirmation query failed", exception);
        }
        if (state.succeeded() && state.amountMatches(expected)) {
            LOG.info(
                    "alipay refund confirmed by query, orderNo={}, refundNo={}, amount={}",
                    order.getOrderNo(),
                    refundNo,
                    expected);
            return refundNo;
        }
        LOG.warn(
                "alipay refund not confirmed, orderNo={}, refundNo={}, status={}, amount={}",
                order.getOrderNo(),
                refundNo,
                state.refundStatus(),
                state.refundAmount());
        throw new ChannelOutcomeUnknownException("alipay refund outcome is not confirmed");
    }

    private BigDecimal parseChannelAmount(String value, String missingMessage) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelOutcomeUnknownException(missingMessage);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ChannelOutcomeUnknownException(
                    "alipay refund returned an invalid amount", exception);
        }
    }

    private String subject(TradeOrder order) {
        TradeOrderItem item = tradeOrderMapper.selectFirstItem(order.getId());
        String title = item == null ? null : item.getCourseTitle();
        String subject = StringUtils.hasText(title) ? "课程：" + title : "课程订单";
        return subject.length() <= SUBJECT_MAX_LENGTH
                ? subject
                : subject.substring(0, SUBJECT_MAX_LENGTH);
    }

    private String amount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal parseRefundAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ChannelOutcomeUnknownException(
                    "alipay refund query returned an invalid amount", exception);
        }
    }

    private String timeoutExpress(TradeOrder order) {
        LocalDateTime expireAt = order.getPayExpireAt();
        if (expireAt == null) {
            return "15m";
        }
        long minutes = Duration.between(LocalDateTime.now(), expireAt).toMinutes();
        return Math.max(1, minutes) + "m";
    }

    private String returnUrl(String orderNo) {
        String base = tradeProperties.getWebBaseUrl();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/orders/" + orderNo + "/result";
    }
}
