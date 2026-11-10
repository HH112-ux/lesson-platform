package com.lp.trade.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.CourseAuditClient;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.entity.AuditOutbox;
import com.lp.trade.mapper.AuditOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the refund audit outbox relay.
 *
 * <p>The relay is the compensation for the only audit action that cannot share a transaction with
 * its status update. Its contract is: deliver at least once, retry with backoff, stop after the
 * attempt limit, and never let a read failure kill the scheduler.
 *
 * @author Caspian
 * @since 0.6.0
 */
@ExtendWith(MockitoExtension.class)
class AuditOutboxRelayTest {

    private static final long OUTBOX_ID = 31L;

    private static final long REFUND_ID = 21L;

    @Mock private AuditOutboxMapper auditOutboxMapper;

    @Mock private CourseAuditClient courseAuditClient;

    private TradeProperties tradeProperties;

    private AuditOutboxRelay relay;

    /**
     * Builds the relay under test with a real retry limit and a simple meter registry.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @BeforeEach
    void setUp() {
        tradeProperties = new TradeProperties();
        tradeProperties.setOutboxMaxRetries(3);
        relay =
                new AuditOutboxRelay(
                        auditOutboxMapper,
                        courseAuditClient,
                        tradeProperties,
                        new SimpleMeterRegistry());
    }

    /**
     * A successful delivery marks the intent delivered and nothing else.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void marksDeliveredOnSuccess() {
        AuditOutbox intent = intent(0);
        when(auditOutboxMapper.selectPending(50)).thenReturn(List.of(intent));

        relay.relay();

        verify(courseAuditClient).append(intent);
        verify(auditOutboxMapper).markDelivered(eq(OUTBOX_ID), any(LocalDateTime.class));
        verify(auditOutboxMapper, never())
                .markRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
        verify(auditOutboxMapper, never()).markGivenUp(anyLong(), anyInt(), anyString());
    }

    /**
     * A failed delivery schedules the next attempt with an incremented counter.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void schedulesRetryOnFailure() {
        AuditOutbox intent = intent(0);
        when(auditOutboxMapper.selectPending(50)).thenReturn(List.of(intent));
        doThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR))
                .when(courseAuditClient)
                .append(intent);

        relay.relay();

        verify(auditOutboxMapper)
                .markRetry(eq(OUTBOX_ID), eq(1), any(LocalDateTime.class), anyString());
        verify(auditOutboxMapper, never()).markDelivered(anyLong(), any());
    }

    /**
     * Reaching the attempt limit stops the loop and leaves the intent for manual repair.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void givesUpAfterAttemptLimit() {
        AuditOutbox intent = intent(2);
        when(auditOutboxMapper.selectPending(50)).thenReturn(List.of(intent));
        doThrow(new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR))
                .when(courseAuditClient)
                .append(intent);

        relay.relay();

        verify(auditOutboxMapper).markGivenUp(eq(OUTBOX_ID), eq(3), anyString());
        verify(auditOutboxMapper, never())
                .markRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString());
    }

    /**
     * A failing read is logged and swallowed so the schedule keeps running.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void swallowsReadFailure() {
        when(auditOutboxMapper.selectPending(50))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> relay.relay());
        verify(courseAuditClient, never()).append(any(AuditOutbox.class));
    }

    /**
     * Builds a pending refund rejection intent.
     *
     * @param retryCount attempts already failed
     * @return intent row
     * @author Caspian
     * @since 0.6.0
     */
    private static AuditOutbox intent(int retryCount) {
        AuditOutbox outbox = new AuditOutbox();
        outbox.setId(OUTBOX_ID);
        outbox.setDedupeKey("refund-review:" + REFUND_ID + ":1700000000000");
        outbox.setOperatorId(9001L);
        outbox.setOperatorRole(0);
        outbox.setActionType(3);
        outbox.setObjectType(1);
        outbox.setObjectId(REFUND_ID);
        outbox.setFromStatus(0);
        outbox.setToStatus(3);
        outbox.setRemark("duplicate purchase");
        outbox.setTraceId("trace-refund-1");
        outbox.setOccurredAt(LocalDateTime.now());
        outbox.setStatus(0);
        outbox.setRetryCount(retryCount);
        outbox.setNextRetryAt(LocalDateTime.now());
        return outbox;
    }
}
