package com.lp.trade.idempotency;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * Validates and resolves client supplied idempotency keys.
 *
 * <p>The order endpoint accepts the key in the {@code Idempotency-Key} header and, for one
 * deprecation window, in the legacy body field. The header wins; sending both with different values
 * is a client bug and is rejected instead of guessed.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class IdempotencyKeys {

    /** Maximum key length, aligned with the {@code trade_order.idempotency_key} column. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]+");

    private IdempotencyKeys() {}

    /**
     * Resolves the effective key from the header and the deprecated body field.
     *
     * @param headerKey value of the {@code Idempotency-Key} header, may be absent
     * @param bodyKey legacy body field, may be absent
     * @return validated key
     * @throws BizException with {@code 40001} when both are present and differ, or when the
     *     effective key is missing or malformed
     * @author Caspian
     * @since 0.4.0
     */
    public static String resolve(String headerKey, String bodyKey) {
        String header = normalize(headerKey);
        String body = normalize(bodyKey);
        if (header != null && body != null && !header.equals(body)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "Idempotency-Key header and body idempotencyKey differ");
        }
        return require(header != null ? header : body);
    }

    /**
     * Validates one key without applying any transport precedence.
     *
     * @param rawKey client supplied key, may be absent
     * @return trimmed key
     * @throws BizException with {@code 40001} when the key is missing, too long or malformed
     * @author Caspian
     * @since 0.4.0
     */
    public static String require(String rawKey) {
        String key = normalize(rawKey);
        if (key == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key is required");
        }
        if (key.length() > MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key is too long");
        }
        if (!ALLOWED.matcher(key).matches()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "Idempotency-Key contains unsupported characters");
        }
        return key;
    }

    private static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim();
    }
}
