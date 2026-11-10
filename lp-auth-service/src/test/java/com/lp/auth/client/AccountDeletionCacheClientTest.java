package com.lp.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies the invalidation call targets the trade internal route and reports failures.
 *
 * <p>The single worker thread is shut down after each call, which lets the test wait for the
 * background task instead of polling.
 *
 * @author Caspian
 * @since 0.3.0
 */
class AccountDeletionCacheClientTest {

    private static final long USER_ID = 2002L;

    private static final String EXPECTED_URL =
            "http://trade.local/internal/v1/account-deletion/state?userId=" + USER_ID;

    private MockRestServiceServer server;

    private SimpleMeterRegistry meterRegistry;

    private ExecutorService executor;

    private AccountDeletionCacheClient client;

    /**
     * Builds a client over a mocked transport and a single background worker.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade.local");
        server = MockRestServiceServer.bindTo(builder).build();
        meterRegistry = new SimpleMeterRegistry();
        executor = Executors.newSingleThreadExecutor();
        client = new AccountDeletionCacheClient(builder.build(), meterRegistry, executor);
    }

    /**
     * Stops the worker even when a test fails before draining it.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Sends a DELETE to the trade internal route and counts nothing on success.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void invalidatesTradeCache() throws InterruptedException {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        client.invalidate(USER_ID);
        awaitWorker();

        server.verify();
        assertThat(
                        meterRegistry
                                .find(AccountDeletionCacheClient.METRIC_INVALIDATE_FAILED)
                                .counter())
                .isNull();
    }

    /**
     * Counts a failed invalidation instead of propagating it to the caller.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void countsFailureWhenTradeErrors() throws InterruptedException {
        server.expect(requestTo(EXPECTED_URL)).andRespond(withServerError());

        client.invalidate(USER_ID);
        awaitWorker();

        assertThat(
                        meterRegistry
                                .get(AccountDeletionCacheClient.METRIC_INVALIDATE_FAILED)
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    private void awaitWorker() throws InterruptedException {
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
