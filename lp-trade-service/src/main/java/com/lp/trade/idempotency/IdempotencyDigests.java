package com.lp.trade.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the stable request digests an idempotency key is bound to.
 *
 * <p>A key replayed with a different digest is rejected with {@code 40901}; the digest never
 * includes the user id because the Redis key is already scoped by user. Every payload starts with
 * {@code v1} so a future canonicalization change can be rolled out without misreading old entries.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class IdempotencyDigests {

    private IdempotencyDigests() {}

    /**
     * Digests an order creation request.
     *
     * @param courseId requested course
     * @param couponId optional coupon
     * @return lowercase hex SHA-256 digest
     * @author Caspian
     * @since 0.4.0
     */
    public static String order(long courseId, Long couponId) {
        return sha256("v1|course=" + courseId + "|coupon=" + (couponId == null ? "-" : couponId));
    }

    /**
     * Digests a payment initiation request.
     *
     * @param orderNo business order number from the path
     * @return lowercase hex SHA-256 digest
     * @author Caspian
     * @since 0.4.0
     */
    public static String payment(String orderNo) {
        return sha256("v1|orderNo=" + (orderNo == null ? "-" : orderNo));
    }

    /**
     * Digests a refund application request.
     *
     * <p>The reason is length prefixed so free text containing the field separator cannot collide
     * with a different request shape.
     *
     * @param orderId order primary key from the path
     * @param amountFen optional refund amount
     * @param reason optional buyer supplied reason
     * @return lowercase hex SHA-256 digest
     * @author Caspian
     * @since 0.4.0
     */
    public static String refund(long orderId, Long amountFen, String reason) {
        String amount = amountFen == null ? "-" : String.valueOf(amountFen);
        String text = reason == null ? "-" : reason.trim().length() + ":" + reason.trim();
        return sha256("v1|orderId=" + orderId + "|amountFen=" + amount + "|reason=" + text);
    }

    /**
     * Computes the lowercase hex SHA-256 digest of a payload.
     *
     * @param payload canonical payload text
     * @return lowercase hex digest
     * @author Caspian
     * @since 0.4.0
     */
    public static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in every JCA implementation, so this cannot happen at runtime.
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
