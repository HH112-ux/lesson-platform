package com.lp.trade.payment.alipay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.lp.common.exception.BizException;
import com.lp.trade.config.AlipayPayProperties;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.TradeOrder;
import com.lp.trade.entity.TradeOrderItem;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.payment.ChannelTradeState;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the Alipay adapter builds a signed page pay form from persisted data only.
 *
 * <p>The form is generated locally by the SDK, so the test proves the wiring, the amount format,
 * the return URL and the signature without contacting the sandbox.
 *
 * @author Caspian
 * @since 0.4.0
 */
class AlipayPaymentGatewayTest {

    private static final String APP_ID = "2021000000000001";

    private static final String ORDER_NO = "TR202611111200000000009";

    private static final String PAY_NO = "PY202611111200000000009";

    private static final String NOTIFY_URL = "https://example.test/api/v1/payments/alipay/notify";

    private static String privateKey;

    private static String publicKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private TradeOrderMapper mapper() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setOrderNo(ORDER_NO);
        order.setPayExpireAt(LocalDateTime.now().plusMinutes(10));
        TradeOrderItem item = new TradeOrderItem();
        item.setCourseTitle("Java 入门");
        when(mapper.selectById(1L)).thenReturn(order);
        when(mapper.selectFirstItem(1L)).thenReturn(item);
        return mapper;
    }

    private PaymentRecord payment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(1L);
        payment.setPayNo(PAY_NO);
        payment.setAmount(new BigDecimal("99.00"));
        return payment;
    }

    private AlipayPaymentGateway gateway(TradeOrderMapper mapper, String notifyUrl)
            throws Exception {
        AlipayPayProperties properties = new AlipayPayProperties();
        properties.setNotifyUrl(notifyUrl);
        TradeProperties tradeProperties = new TradeProperties();
        tradeProperties.setWebBaseUrl("http://127.0.0.1:5173");
        return new AlipayPaymentGateway(
                properties,
                tradeProperties,
                mapper,
                new AlipayCredentials(APP_ID, privateKey, publicKey));
    }

    private AlipayPaymentGateway gatewayWith(AlipayClient client) {
        TradeProperties tradeProperties = new TradeProperties();
        tradeProperties.setWebBaseUrl("http://127.0.0.1:5173");
        return new AlipayPaymentGateway(
                new AlipayPayProperties(), tradeProperties, mapper(), client);
    }

    @Test
    void buildsSelfSubmittingForm() throws Exception {
        PrepayResult result = gateway(mapper(), NOTIFY_URL).prepay(payment());
        assertEquals(PaymentChannel.ALIPAY, result.getChannel());
        assertEquals(PAY_NO, result.getPayNo());
        String form = result.getPayParams().get("paymentHtml");
        assertTrue(form.contains("<form"));
        assertTrue(form.contains(ORDER_NO));
        assertTrue(form.contains("99.00"));
        assertTrue(form.contains("return_url"));
        assertTrue(form.contains("notify_url"));
        assertTrue(form.contains("sign"));
        assertEquals(PAY_NO, result.getPayParams().get("payNo"));
        assertEquals("POST", result.getPayParams().get("formMethod"));
    }

    @Test
    void omitsNotifyUrlWhenNotConfigured() throws Exception {
        PrepayResult result = gateway(mapper(), "").prepay(payment());
        assertFalse(result.getPayParams().get("paymentHtml").contains("notify_url"));
    }

    @Test
    void rejectsMissingOrder() throws Exception {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        assertThrows(BizException.class, () -> gateway(mapper, NOTIFY_URL).prepay(payment()));
    }

    @Test
    void mapsPaidTradeQuery() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTradeNo("2026111122001400000000000001");
        response.setTotalAmount("99.00");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        ChannelTradeState state = gatewayWith(client).query(payment());

        assertTrue(state.paid());
        assertFalse(state.absent());
        assertEquals("2026111122001400000000000001", state.channelTradeNo());
        assertEquals(new BigDecimal("99.00"), state.amount());
    }

    @Test
    void treatsUnknownTradeAsNotFound() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertTrue(gatewayWith(client).query(payment()).absent());
    }

    @Test
    void reportsQueryFailureAsUpstreamError() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        when(client.execute(any(AlipayTradeQueryRequest.class)))
                .thenThrow(new AlipayApiException("timeout"));

        assertThrows(BizException.class, () -> gatewayWith(client).query(payment()));
    }

    @Test
    void reportsInvalidQueryAmountAsUpstreamError() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTotalAmount("not-a-number");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertThrows(BizException.class, () -> gatewayWith(client).query(payment()));
    }

    @Test
    void rejectsQueryWithoutTradeStatus() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeNo("2026111122001400000000000001");
        response.setTotalAmount("99.00");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertThrows(BizException.class, () -> gatewayWith(client).query(payment()));
    }

    @Test
    void acceptsSuccessfulAndUnknownClose() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeCloseResponse success = new AlipayTradeCloseResponse();
        success.setCode("10000");
        AlipayTradeCloseResponse notExist = new AlipayTradeCloseResponse();
        notExist.setCode("40004");
        notExist.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(success, notExist);

        AlipayPaymentGateway gateway = gatewayWith(client);

        assertTrue(gateway.close(payment()));
        assertTrue(gateway.close(payment()));
    }

    @Test
    void reportsRejectedCloseAsUpstreamError() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeCloseResponse rejected = new AlipayTradeCloseResponse();
        rejected.setCode("40004");
        rejected.setSubCode("ACQ.TRADE_STATUS_ERROR");
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(rejected);

        assertThrows(BizException.class, () -> gatewayWith(client).close(payment()));
    }
}
