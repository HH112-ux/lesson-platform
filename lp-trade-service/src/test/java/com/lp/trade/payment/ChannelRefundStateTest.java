package com.lp.trade.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Verifies the read-only refund state semantics used by reconciliation.
 *
 * @author Caspian
 * @since 0.6.0
 */
class ChannelRefundStateTest {

    @Test
    void treatsNotFoundAsNeitherSuccessfulNorMatching() {
        ChannelRefundState state = ChannelRefundState.notFound();

        assertFalse(state.succeeded());
        assertFalse(state.amountMatches(new BigDecimal("99.00")));
        assertFalse(state.amountMatches(null));
    }

    @Test
    void onlyRefundSuccessCountsAsSuccessful() {
        assertTrue(new ChannelRefundState(ChannelRefundState.REFUND_SUCCESS, null).succeeded());
        assertFalse(new ChannelRefundState("REFUND_PROCESSING", null).succeeded());
        assertFalse(new ChannelRefundState(null, null).succeeded());
    }

    @Test
    void comparesAmountsNumerically() {
        ChannelRefundState state = new ChannelRefundState("REFUND_SUCCESS", new BigDecimal("99.0"));

        assertTrue(state.amountMatches(new BigDecimal("99.00")));
        assertFalse(state.amountMatches(new BigDecimal("99.01")));
        assertFalse(
                new ChannelRefundState("REFUND_SUCCESS", null).amountMatches(new BigDecimal("0")));
    }
}
