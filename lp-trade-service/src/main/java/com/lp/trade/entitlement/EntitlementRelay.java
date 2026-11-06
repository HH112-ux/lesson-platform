package com.lp.trade.entitlement;

import com.lp.common.exception.BizException;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.domain.EntitlementAction;
import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers pending outbox rows to learn-service.
 *
 * <p>The relay runs outside any business transaction. Delivery is at-least-once: a crash between
 * the remote call and {@code markDelivered} causes one more attempt, which learn-service absorbs
 * because grant and revoke both decide on the stored row. Failures use exponential backoff; after
 * the configured attempt limit the row is marked as given up and needs manual reconciliation.
 * Grants and revocations share this path, so a refund retries exactly like a purchase.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class EntitlementRelay {

    private static final Logger LOG = LoggerFactory.getLogger(EntitlementRelay.class);

    private static final int BATCH_SIZE = 50;

    private static final int MAX_ERROR_LENGTH = 300;

    private static final long BASE_BACKOFF_SECONDS = 5L;

    private static final long MAX_BACKOFF_SECONDS = 300L;

    private final EntitlementOutboxMapper entitlementOutboxMapper;

    private final LearnEntitlementClient learnEntitlementClient;

    private final TradeProperties tradeProperties;

    private final EntitlementOutboxMetrics metrics;

    /**
     * Creates the relay with its dependencies.
     *
     * @param entitlementOutboxMapper outbox persistence
     * @param learnEntitlementClient learn-service internal client
     * @param tradeProperties retry limit
     * @param metrics outbox gauges refreshed after each batch
     * @author Caspian
     * @since 0.1.0
     */
    public EntitlementRelay(
            EntitlementOutboxMapper entitlementOutboxMapper,
            LearnEntitlementClient learnEntitlementClient,
            TradeProperties tradeProperties,
            EntitlementOutboxMetrics metrics) {
        this.entitlementOutboxMapper = entitlementOutboxMapper;
        this.learnEntitlementClient = learnEntitlementClient;
        this.tradeProperties = tradeProperties;
        this.metrics = metrics;
    }

    /**
     * Delivers one batch of due intents; never throws so the scheduler keeps running.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Scheduled(fixedDelayString = "${lp.trade.outbox-relay-delay-ms:5000}", initialDelay = 10000)
    public void relay() {
        try {
            List<EntitlementOutbox> pending = entitlementOutboxMapper.selectPending(BATCH_SIZE);
            for (EntitlementOutbox outbox : pending) {
                deliver(outbox);
            }
        } catch (RuntimeException exception) {
            LOG.warn("entitlement outbox read failed: {}", exception.getMessage());
        } finally {
            metrics.refresh();
        }
    }

    private void deliver(EntitlementOutbox outbox) {
        try {
            if (Integer.valueOf(EntitlementAction.REVOKE).equals(outbox.getAction())) {
                learnEntitlementClient.revoke(
                        outbox.getUserId(), outbox.getCourseId(), outbox.getOrderId());
            } else {
                learnEntitlementClient.grant(
                        outbox.getUserId(), outbox.getCourseId(), outbox.getOrderId());
            }
            entitlementOutboxMapper.markDelivered(outbox.getId(), LocalDateTime.now());
        } catch (RuntimeException exception) {
            handleFailure(outbox, exception);
        }
    }

    private void handleFailure(EntitlementOutbox outbox, RuntimeException exception) {
        int retryCount = outbox.getRetryCount() + 1;
        String error = describe(exception);
        if (retryCount >= tradeProperties.getOutboxMaxRetries()) {
            entitlementOutboxMapper.markGivenUp(outbox.getId(), retryCount, error);
            LOG.error(
                    "entitlement delivery gave up: outbox={}, order={}, attempts={}",
                    outbox.getId(),
                    outbox.getOrderNo(),
                    retryCount);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
        entitlementOutboxMapper.markRetry(outbox.getId(), retryCount, nextRetryAt, error);
        LOG.warn(
                "entitlement delivery failed: outbox={}, order={}, attempt={}, next={}",
                outbox.getId(),
                outbox.getOrderNo(),
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
