package com.lp.trade.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * Verifies key resolution and request digest rules of the idempotency contract.
 *
 * @author Caspian
 * @since 0.4.0
 */
class IdempotencyContractTest {

    @Test
    void headerWinsWhenOnlyTheHeaderIsPresent() {
        assertEquals("abcdef", IdempotencyKeys.resolve("abcdef", null));
    }

    @Test
    void legacyBodyFieldIsAcceptedWhenTheHeaderIsAbsent() {
        assertEquals("abcdef", IdempotencyKeys.resolve(null, "abcdef"));
        assertEquals("abcdef", IdempotencyKeys.resolve("  ", "abcdef"));
    }

    @Test
    void equalHeaderAndBodyKeysAreAccepted() {
        assertEquals("abcdef", IdempotencyKeys.resolve("abcdef", "abcdef"));
    }

    @Test
    void differentHeaderAndBodyKeysAreRejected() {
        BizException exception =
                assertThrows(BizException.class, () -> IdempotencyKeys.resolve("header", "body"));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
    }

    @Test
    void missingKeyIsRejected() {
        BizException exception =
                assertThrows(BizException.class, () -> IdempotencyKeys.resolve(null, null));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
    }

    @Test
    void overlongOrMalformedKeysAreRejected() {
        String tooLong = "a".repeat(IdempotencyKeys.MAX_LENGTH + 1);
        assertEquals(
                ErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> IdempotencyKeys.require(tooLong)).getCode());
        assertEquals(
                ErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> IdempotencyKeys.require("bad key!"))
                        .getCode());
    }

    @Test
    void keysAreTrimmed() {
        assertEquals("abcdef", IdempotencyKeys.require("  abcdef  "));
    }

    @Test
    void orderDigestIsStableAndBindsCourseAndCoupon() {
        assertEquals(IdempotencyDigests.order(1L, null), IdempotencyDigests.order(1L, null));
        assertNotEquals(IdempotencyDigests.order(1L, null), IdempotencyDigests.order(2L, null));
        assertNotEquals(IdempotencyDigests.order(1L, null), IdempotencyDigests.order(1L, 9L));
    }

    @Test
    void refundDigestBindsAmountAndNormalizedReason() {
        assertEquals(
                IdempotencyDigests.refund(1L, 100L, " broken "),
                IdempotencyDigests.refund(1L, 100L, "broken"));
        assertNotEquals(
                IdempotencyDigests.refund(1L, 100L, "broken"),
                IdempotencyDigests.refund(1L, 200L, "broken"));
        assertNotEquals(
                IdempotencyDigests.refund(1L, 100L, "broken"),
                IdempotencyDigests.refund(1L, 100L, "changed"));
    }
}
