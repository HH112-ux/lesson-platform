package com.lp.trade.payment.alipay;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alipay.api.internal.util.AlipaySignature;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Alipay callback verifier with locally signed bodies.
 *
 * <p>Every negative body is signed with the real key, so each rejection proves the claimed check
 * failed rather than the signature.
 *
 * @author Caspian
 * @since 0.4.0
 */
class AlipaySignatureVerifierTest {

    private static final String APP_ID = "2021000000000001";

    private static final String SELLER_ID = "2088000000000001";

    private static String privateKey;

    private static String publicKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPair pair = keyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    @Test
    void acceptsAuthenticNotification() throws Exception {
        verifier(publicKey).verify(PaymentChannel.ALIPAY, Map.of(), signedBody(parameters()), null);
    }

    @Test
    void rejectsTamperedField() throws Exception {
        assertRejected(signedBody(parameters()).replace("99.00", "1.00"));
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        String body = signedBody(parameters());
        assertRejected(body.substring(0, body.indexOf("&sign=")));
    }

    @Test
    void rejectsForeignKey() throws Exception {
        String foreignKey = Base64.getEncoder().encodeToString(keyPair().getPublic().getEncoded());
        assertRejected(verifier(foreignKey), signedBody(parameters()));
    }

    @Test
    void rejectsNonRsa2SignType() throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>(parameters());
        parameters.put("sign_type", "RSA1");
        assertRejected(signedBody(parameters));
    }

    @Test
    void rejectsForeignMerchantIdentity() throws Exception {
        Map<String, String> foreignApp = new LinkedHashMap<>(parameters());
        foreignApp.put("app_id", "2021000000000099");
        assertRejected(signedBody(foreignApp));
        Map<String, String> foreignSeller = new LinkedHashMap<>(parameters());
        foreignSeller.put("seller_id", "2088000000000099");
        assertRejected(signedBody(foreignSeller));
    }

    @Test
    void rejectsMalformedBody() {
        assertRejected("not-a-form");
    }

    @Test
    void mapsVerifiedFormToNotification() {
        PaymentNotification notification =
                AlipayNotifyForm.parse("out_trade_no=TR1&trade_no=T1&total_amount=99.00")
                        .toNotification();
        assertEquals("TR1", notification.getOrderNo());
        assertEquals("T1", notification.getChannelTradeNo());
        assertEquals("99.00", notification.getAmount().toPlainString());
    }

    private static AlipaySignatureVerifier verifier(String alipayPublicKey) {
        return new AlipaySignatureVerifier(
                new AlipayCredentials(APP_ID, privateKey, alipayPublicKey), SELLER_ID);
    }

    private static void assertRejected(String body) {
        assertRejected(verifier(publicKey), body);
    }

    private static void assertRejected(AlipaySignatureVerifier verifier, String body) {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> verifier.verify(PaymentChannel.ALIPAY, Map.of(), body, null));
        assertEquals(ErrorCode.PAYMENT_SIGNATURE_INVALID.getCode(), exception.getCode());
    }

    private static Map<String, String> parameters() {
        return Map.of(
                "app_id", APP_ID,
                "seller_id", SELLER_ID,
                "out_trade_no", "TR202611111200000000010",
                "trade_no", "2026111122001400000000000001",
                "trade_status", "TRADE_SUCCESS",
                "total_amount", "99.00");
    }

    // Signs the parameters the way Alipay does, keeping sign last so it can be stripped.
    private static String signedBody(Map<String, String> parameters) throws Exception {
        Map<String, String> form = new LinkedHashMap<>(parameters);
        form.putIfAbsent("sign_type", "RSA2");
        Map<String, String> content = new LinkedHashMap<>(form);
        content.remove("sign_type");
        form.put(
                "sign",
                AlipaySignature.rsaSign(
                        AlipaySignature.getSignContent(content), privateKey, "UTF-8", "RSA2"));
        return form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
