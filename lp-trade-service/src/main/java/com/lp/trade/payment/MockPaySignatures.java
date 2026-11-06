package com.lp.trade.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Canonical payload and HMAC helpers shared by the mock gateway and the mock verifier.
 *
 * <p>Both sides must serialize the callback fields in exactly the same order; otherwise a valid
 * local payment could never verify. The canonical form deliberately contains only server owned
 * fields, so a client cannot move a payment to another order by rewriting the callback URL.
 *
 * @author Caspian
 * @since 0.2.0
 */
public final class MockPaySignatures {

    /**
     * Header carrying the HMAC signature of a local callback.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final String SIGNATURE_HEADER = "X-Mock-Signature";

    private static final String ALGORITHM = "HmacSHA256";

    private MockPaySignatures() {}

    /**
     * Builds the canonical string covered by the signature.
     *
     * @param payNo platform payment number
     * @param orderNo platform order number
     * @param amount amount in yuan
     * @return canonical string
     * @author Caspian
     * @since 0.2.0
     */
    public static String canonical(String payNo, String orderNo, String amount) {
        return "payNo=" + payNo + "&orderNo=" + orderNo + "&amount=" + amount;
    }

    /**
     * Signs a canonical string with the shared secret.
     *
     * @param secret shared secret
     * @param canonical canonical string
     * @return lower case hexadecimal HMAC
     * @author Caspian
     * @since 0.2.0
     */
    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of()
                    .formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }

    /**
     * Compares a received signature with the expected one in constant time.
     *
     * @param secret shared secret
     * @param canonical canonical string
     * @param signature received signature
     * @return {@code true} when the signature matches
     * @author Caspian
     * @since 0.2.0
     */
    public static boolean matches(String secret, String canonical, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(secret, canonical).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * URL-encodes one query value.
     *
     * @param value raw value
     * @return encoded value
     * @author Caspian
     * @since 0.2.0
     */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
