package com.lp.trade.entitlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.trade.config.TradeProperties;
import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the relay, focused on the manual requeue path.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class EntitlementRelayTest {

    private static final long OUTBOX_ID = 41L;

    @Mock private EntitlementOutboxMapper entitlementOutboxMapper;

    @Mock private LearnEntitlementClient learnEntitlementClient;

    private EntitlementRelay relay;

    /**
     * Builds the relay with a real metrics component.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        EntitlementOutboxMetrics metrics =
                new EntitlementOutboxMetrics(entitlementOutboxMapper, new SimpleMeterRegistry());
        relay =
                new EntitlementRelay(
                        entitlementOutboxMapper,
                        learnEntitlementClient,
                        new TradeProperties(),
                        metrics);
    }

    /**
     * A requeued row is delivered by the next relay pass and marked delivered.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void requeuedRowIsDeliveredOnNextPass() {
        when(entitlementOutboxMapper.selectPending(anyInt())).thenReturn(List.of(requeued()));

        relay.relay();

        verify(learnEntitlementClient).grant(1001L, 7L, 990001L);
        verify(entitlementOutboxMapper).markDelivered(eq(OUTBOX_ID), any(LocalDateTime.class));
    }

    /**
     * A failed delivery records the attempt count and the reason.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void failedDeliveryRecordsAttemptAndReason() {
        when(entitlementOutboxMapper.selectPending(anyInt())).thenReturn(List.of(requeued()));
        org.mockito.Mockito.doThrow(new IllegalStateException("down"))
                .when(learnEntitlementClient)
                .grant(anyLong(), anyLong(), anyLong());

        relay.relay();

        verify(entitlementOutboxMapper)
                .markRetry(
                        eq(OUTBOX_ID),
                        eq(1),
                        any(LocalDateTime.class),
                        eq("IllegalStateException"));
    }

    private EntitlementOutbox requeued() {
        EntitlementOutbox outbox = new EntitlementOutbox();
        outbox.setId(OUTBOX_ID);
        outbox.setOrderId(990001L);
        outbox.setOrderNo("LP202603010001");
        outbox.setUserId(1001L);
        outbox.setCourseId(7L);
        outbox.setStatus(0);
        outbox.setRetryCount(0);
        return outbox;
    }
}
