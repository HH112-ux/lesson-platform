package com.lp.trade.audit;

import com.lp.common.exception.BizException;
import com.lp.trade.client.CourseAuditClient;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.entity.AuditOutbox;
import com.lp.trade.mapper.AuditOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers pending refund audit intents to the unified audit table in course-service.
 *
 * <p>This relay is the compensation for the one action that cannot share a transaction with the
 * audit row: a refund decision commits in {@code lp_oms} while the audit table lives in {@code
 * lp_cms}. The decision transaction writes the intent, so "status changed but no audit intent" is
 * impossible; this relay then closes the remaining gap. Delivery is at-least-once and {@code
 * dedupe_key} makes redelivery harmless. Failures back off exponentially; after the configured
 * attempt limit the intent is marked as given up and logged at error level for manual
 * reconciliation.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Component
public class AuditOutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(AuditOutboxRelay.class);

    private static final int BATCH_SIZE = 50;

    private static final int MAX_ERROR_LENGTH = 300;

    private static final long BASE_BACKOFF_SECONDS = 5L;

    private static final long MAX_BACKOFF_SECONDS = 300L;

    private final AuditOutboxMapper auditOutboxMapper;

    private final CourseAuditClient courseAuditClient;

    private final TradeProperties tradeProperties;

    private final MeterRegistry meterRegistry;

    /**
     * Creates the relay with its dependencies.
     *
     * @param auditOutboxMapper outbox persistence
     * @param courseAuditClient course-service internal audit client
     * @param tradeProperties retry limit
     * @param meterRegistry registry counting deliveries, retries and give-ups
     * @author Caspian
     * @since 0.6.0
     */
    public AuditOutboxRelay(
            AuditOutboxMapper auditOutboxMapper,
            CourseAuditClient courseAuditClient,
            TradeProperties tradeProperties,
            MeterRegistry meterRegistry) {
        this.auditOutboxMapper = auditOutboxMapper;
        this.courseAuditClient = courseAuditClient;
        this.tradeProperties = tradeProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Delivers one batch of due intents; never throws so the scheduler keeps running.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Scheduled(fixedDelayString = "${lp.trade.outbox-relay-delay-ms:5000}", initialDelay = 12000)
    public void relay() {
        try {
            List<AuditOutbox> pending = auditOutboxMapper.selectPending(BATCH_SIZE);
            for (AuditOutbox outbox : pending) {
                deliver(outbox);
            }
        } catch (RuntimeException exception) {
            LOG.warn("audit outbox read failed: {}", exception.getMessage());
        }
    }

    private void deliver(AuditOutbox outbox) {
        try {
            courseAuditClient.append(outbox);
            auditOutboxMapper.markDelivered(outbox.getId(), LocalDateTime.now());
            meterRegistry.counter("lp.audit.outbox.delivered").increment();
        } catch (RuntimeException exception) {
            handleFailure(outbox, exception);
        }
    }

    private void handleFailure(AuditOutbox outbox, RuntimeException exception) {
        int previous = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        int retryCount = previous + 1;
        String error = describe(exception);
        if (retryCount >= tradeProperties.getOutboxMaxRetries()) {
            auditOutboxMapper.markGivenUp(outbox.getId(), retryCount, error);
            meterRegistry.counter("lp.audit.outbox.given_up").increment();
            LOG.error(
                    "audit delivery gave up: outbox={}, refund={}, attempts={}",
                    outbox.getId(),
                    outbox.getObjectId(),
                    retryCount);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
        auditOutboxMapper.markRetry(outbox.getId(), retryCount, nextRetryAt, error);
        meterRegistry.counter("lp.audit.outbox.retried").increment();
        LOG.warn(
                "audit delivery failed: outbox={}, refund={}, attempt={}, next={}",
                outbox.getId(),
                outbox.getObjectId(),
                retryCount,
                nextRetryAt);
    }

    private static long backoffSeconds(int retryCount) {
        long seconds = BASE_BACKOFF_SECONDS << Math.min(retryCount, 6);
        return Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private static String describe(RuntimeException exception) {
        String message =
                exception instanceof BizException
                        ? "upstream error code " + ((BizException) exception).getCode()
                        : exception.getClass().getSimpleName();
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
    }
}
