package com.lp.auth.client;

import com.lp.auth.config.AccountDeletionProperties;
import com.lp.common.internal.InternalHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Drops the trade-side cached cooling-off state right after a request changes.
 *
 * <p>Invalidation is a side effect, never part of the answer: the call runs on a background thread
 * with a 200 ms read timeout, so a slow or dead trade service cannot lengthen the user's request.
 * When the call fails the trade cache keeps its previous answer until the 45 second TTL expires,
 * which is the documented worst case window. Failures are counted and logged instead of retried
 * here; the TTL is the retry.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Component
public class AccountDeletionCacheClient {

    /** Metric counting cache invalidations that trade did not accept. */
    public static final String METRIC_INVALIDATE_FAILED =
            "lp.auth.account_cancellation.cache_invalidate.failed";

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionCacheClient.class);

    private static final int WORKER_THREADS = 2;

    private final RestClient restClient;

    private final MeterRegistry meterRegistry;

    private final ExecutorService executor;

    /**
     * Creates the client from the account cancellation settings.
     *
     * @param properties trade-service address and invalidation timeout
     * @param internalToken shared secret required by the trade-service internal API
     * @param meterRegistry registry for the invalidation failure counter
     * @author Caspian
     * @since 0.3.0
     */
    @Autowired
    public AccountDeletionCacheClient(
            AccountDeletionProperties properties,
            @Value("${lp.internal.token}") String internalToken,
            MeterRegistry meterRegistry) {
        this(restClientOf(properties, internalToken), meterRegistry, newExecutor());
    }

    /**
     * Creates the client over prepared collaborators; used by tests to stub the transport.
     *
     * @param restClient client already bound to the trade-service base URL
     * @param meterRegistry registry for the invalidation failure counter
     * @param executor executor running the invalidation off the request thread
     * @author Caspian
     * @since 0.3.0
     */
    AccountDeletionCacheClient(
            RestClient restClient, MeterRegistry meterRegistry, ExecutorService executor) {
        this.restClient = restClient;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /**
     * Requests invalidation without waiting for it.
     *
     * <p>Call this only after the state change committed; otherwise a rollback would clear a cache
     * that still matches the database.
     *
     * @param userId account owner whose cached state must be dropped
     * @author Caspian
     * @since 0.3.0
     */
    public void invalidate(long userId) {
        try {
            executor.execute(() -> deleteState(userId));
        } catch (RejectedExecutionException exception) {
            recordFailure(userId, exception);
        }
    }

    /**
     * Stops the background workers when the service shuts down.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void deleteState(long userId) {
        try {
            restClient
                    .delete()
                    .uri(
                            builder ->
                                    builder.path("/internal/v1/account-deletion/state")
                                            .queryParam("userId", userId)
                                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            recordFailure(userId, exception);
        }
    }

    private void recordFailure(long userId, RuntimeException exception) {
        meterRegistry.counter(METRIC_INVALIDATE_FAILED).increment();
        LOG.warn(
                "account cancellation cache invalidation failed, worst case window is the cache ttl "
                        + "userId={}",
                userId,
                exception);
    }

    private static ExecutorService newExecutor() {
        ThreadFactory factory =
                runnable -> {
                    Thread thread = new Thread(runnable, "account-cancellation-invalidate");
                    thread.setDaemon(true);
                    return thread;
                };
        return Executors.newFixedThreadPool(WORKER_THREADS, factory);
    }

    private static RestClient restClientOf(
            AccountDeletionProperties properties, String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.getCacheInvalidateReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getTradeBaseUrl())
                .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                .requestFactory(factory)
                .build();
    }
}
