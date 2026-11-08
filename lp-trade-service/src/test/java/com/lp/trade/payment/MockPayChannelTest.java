package com.lp.trade.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.common.exception.BizException;
import com.lp.trade.config.MockPayProperties;
import com.lp.trade.domain.PaymentChannel;
import com.lp.trade.dto.PaymentNotification;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies the local mock payment signer, verifier and profile guard.
 *
 * <p>The negative cases are the important ones: a forged, tampered or missing signature must never
 * pass, because the callback path would otherwise mark an order paid without a real payment.
 *
 * @author Caspian
 * @since 0.2.0
 */
class MockPayChannelTest {

    private static final String SECRET = "lp_mock_pay_dev_secret";

    private static final String PAY_NO = "PY202601011200000000001";

    private static final String ORDER_NO = "TR202601011200000000002";

    private static final String AMOUNT = "99.00";

    private MockPayProperties properties() {
        MockPayProperties properties = new MockPayProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        return properties;
    }

    private PaymentNotification notification() {
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel(PaymentChannel.ALIPAY);
        notification.setOrderNo(ORDER_NO);
        notification.setChannelTradeNo(PAY_NO);
        notification.setAmount(new BigDecimal(AMOUNT));
        return notification;
    }

    @Test
    void signsAndMatchesCanonicalPayload() {
        String canonical = MockPaySignatures.canonical(PAY_NO, ORDER_NO, AMOUNT);
        String signature = MockPaySignatures.sign(SECRET, canonical);
        assertTrue(MockPaySignatures.matches(SECRET, canonical, signature));
        assertTrue(!MockPaySignatures.matches(SECRET, canonical + "&extra=1", signature));
        assertTrue(!MockPaySignatures.matches(SECRET, canonical, null));
    }

    @Test
    void acceptsValidSignature() {
        String canonical = MockPaySignatures.canonical(PAY_NO, ORDER_NO, AMOUNT);
        String signature = MockPaySignatures.sign(SECRET, canonical);
        MockSignatureVerifier verifier = new MockSignatureVerifier(properties());
        assertDoesNotThrow(
                () ->
                        verifier.verify(
                                PaymentChannel.ALIPAY,
                                Map.of(MockPaySignatures.SIGNATURE_HEADER, signature),
                                canonical,
                                notification()));
    }

    @Test
    void rejectsTamperedPayload() {
        String signed = MockPaySignatures.canonical(PAY_NO, ORDER_NO, AMOUNT);
        String signature = MockPaySignatures.sign(SECRET, signed);
        MockSignatureVerifier verifier = new MockSignatureVerifier(properties());
        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                verifier.verify(
                                        PaymentChannel.ALIPAY,
                                        Map.of(MockPaySignatures.SIGNATURE_HEADER, signature),
                                        MockPaySignatures.canonical(PAY_NO, ORDER_NO, "1.00"),
                                        notification()));
        assertEquals(62016, exception.getCode());
    }

    @Test
    void rejectsMissingSignature() {
        MockSignatureVerifier verifier = new MockSignatureVerifier(properties());
        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                verifier.verify(
                                        PaymentChannel.ALIPAY,
                                        Map.of(),
                                        MockPaySignatures.canonical(PAY_NO, ORDER_NO, AMOUNT),
                                        notification()));
        assertEquals(62016, exception.getCode());
    }

    @Test
    void rejectsForeignChannel() {
        String canonical = MockPaySignatures.canonical(PAY_NO, ORDER_NO, AMOUNT);
        String signature = MockPaySignatures.sign(SECRET, canonical);
        MockSignatureVerifier verifier = new MockSignatureVerifier(properties());
        assertThrows(
                BizException.class,
                () ->
                        verifier.verify(
                                999,
                                Map.of(MockPaySignatures.SIGNATURE_HEADER, signature),
                                canonical,
                                notification()));
    }

    @Test
    void rejectsBlankSecret() {
        MockPayProperties properties = new MockPayProperties();
        properties.setEnabled(true);

        assertThrows(IllegalStateException.class, () -> new MockSignatureVerifier(properties));
    }

    @Test
    void guardRejectsProdProfile() {
        MockPayProfileGuard guard =
                new MockPayProfileGuard(
                        new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        assertThrows(IllegalStateException.class, guard::verifyProfile);
    }

    @Test
    void guardAllowsDevProfile() {
        MockPayProfileGuard guard =
                new MockPayProfileGuard(
                        new MockEnvironment().withProperty("spring.profiles.active", "dev"));
        assertDoesNotThrow(guard::verifyProfile);
    }
}
