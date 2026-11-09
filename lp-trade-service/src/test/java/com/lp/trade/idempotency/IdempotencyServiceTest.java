package com.lp.trade.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the replay, conflict and degradation behaviour of {@link IdempotencyService}.
 *
 * @author Caspian
 * @since 0.4.0
 */
class IdempotencyServiceTest {

    private final FakeStore store = new FakeStore();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private IdempotencyService service;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(store, objectMapper, meterRegistry, 5_000L, 20L);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void replaysTheFirstSuccessfulResult() {
        AtomicInteger calls = new AtomicInteger();

        IdempotencyOutcome<Payload> first = createOrder(calls);
        IdempotencyOutcome<Payload> second = createOrder(calls);

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.value(), second.value());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsTheSameKeyUsedWithADifferentRequest() {
        AtomicInteger calls = new AtomicInteger();
        createOrder(calls);

        BizException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BizException.class,
                        () ->
                                service.execute(
                                        IdempotencyScope.ORDER_CREATE,
                                        7L,
                                        "key-1",
                                        "v1|course=999",
                                        Payload.class,
                                        () -> new Payload("other", 1)));

        assertEquals(ErrorCode.STATE_CONFLICT.getCode(), exception.getCode());
        assertEquals(1, calls.get());
    }

    @Test
    void releasesTheLeaseAndForgetsFailedAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () ->
                        service.execute(
                                IdempotencyScope.REFUND_APPLY,
                                7L,
                                "key-1",
                                "digest",
                                Payload.class,
                                () -> {
                                    attempts.incrementAndGet();
                                    throw new BizException(ErrorCode.PARAM_INVALID, "bad reason");
                                }));

        IdempotencyOutcome<Payload> recovered =
                service.execute(
                        IdempotencyScope.REFUND_APPLY,
                        7L,
                        "key-1",
                        "digest",
                        Payload.class,
                        () -> {
                            attempts.incrementAndGet();
                            return new Payload("refund-1", 100);
                        });

        assertEquals(2, attempts.get());
        assertFalse(recovered.replayed());
        assertEquals("refund-1", recovered.value().id());
    }

    @Test
    void concurrentRequestWaitsAndReceivesTheSameResult() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);

        var first =
                executor.submit(
                        () ->
                                service.execute(
                                        IdempotencyScope.PAYMENT_INITIATE,
                                        7L,
                                        "key-1",
                                        "digest",
                                        Payload.class,
                                        () -> {
                                            firstStarted.countDown();
                                            sleep(200L);
                                            calls.incrementAndGet();
                                            return new Payload("pay-1", 300);
                                        }));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

        IdempotencyOutcome<Payload> second =
                service.execute(
                        IdempotencyScope.PAYMENT_INITIATE,
                        7L,
                        "key-1",
                        "digest",
                        Payload.class,
                        () -> {
                            calls.incrementAndGet();
                            return new Payload("pay-2", 300);
                        });

        assertEquals(first.get().value(), second.value());
        assertTrue(second.replayed());
        assertEquals(1, calls.get());
    }

    @Test
    void reportsInProgressWhenTheFirstRequestOutlivesTheWaitWindow() {
        store.acquireLease(
                IdempotencyScope.ORDER_CREATE,
                7L,
                keyHash("key-1"),
                "another-owner",
                Duration.ofSeconds(60));
        IdempotencyService shortWait =
                new IdempotencyService(store, objectMapper, meterRegistry, 150L, 20L);

        BizException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        BizException.class,
                        () ->
                                shortWait.execute(
                                        IdempotencyScope.ORDER_CREATE,
                                        7L,
                                        "key-1",
                                        "digest",
                                        Payload.class,
                                        () -> new Payload("never", 0)));

        assertEquals(ErrorCode.IDEMPOTENCY_IN_PROGRESS.getCode(), exception.getCode());
    }

    @Test
    void fallsBackToTheActionWhenTheStoreIsUnavailable() {
        store.unavailable = true;
        AtomicInteger calls = new AtomicInteger();

        IdempotencyOutcome<Payload> outcome = createOrder(calls);

        assertFalse(outcome.replayed());
        assertEquals(1, calls.get());
        assertEquals(
                1.0,
                meterRegistry
                        .get("lp.trade.idempotency.degraded")
                        .tag("scope", "order-create")
                        .counter()
                        .count());
    }

    private IdempotencyOutcome<Payload> createOrder(AtomicInteger calls) {
        return service.execute(
                IdempotencyScope.ORDER_CREATE,
                7L,
                "key-1",
                IdempotencyDigests.order(1L, null),
                Payload.class,
                () -> {
                    calls.incrementAndGet();
                    return new Payload("order-1", 100);
                });
    }

    private String keyHash(String key) {
        return IdempotencyDigests.sha256(key);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Response payload used by the tests.
     *
     * @param id identifier of the created resource
     * @param amountFen amount carried by the response
     * @author Caspian
     * @since 0.4.0
     */
    public record Payload(String id, long amountFen) {}

    /**
     * In-memory store with controllable outage.
     *
     * @author Caspian
     * @since 0.4.0
     */
    static final class FakeStore implements IdempotencyStore {

        private final Map<String, StoredIdempotentResult> results = new ConcurrentHashMap<>();

        private final Set<String> leases = ConcurrentHashMap.newKeySet();

        private volatile boolean unavailable;

        @Override
        public Optional<StoredIdempotentResult> findResult(
                IdempotencyScope scope, long userId, String keyHash) {
            failIfUnavailable();
            return Optional.ofNullable(results.get(key(scope, userId, keyHash)));
        }

        @Override
        public boolean acquireLease(
                IdempotencyScope scope,
                long userId,
                String keyHash,
                String ownerToken,
                Duration ttl) {
            failIfUnavailable();
            return leases.add(key(scope, userId, keyHash));
        }

        @Override
        public void saveResult(
                IdempotencyScope scope,
                long userId,
                String keyHash,
                StoredIdempotentResult result,
                Duration ttl) {
            failIfUnavailable();
            results.put(key(scope, userId, keyHash), result);
        }

        @Override
        public void releaseLease(
                IdempotencyScope scope, long userId, String keyHash, String ownerToken) {
            failIfUnavailable();
            leases.remove(key(scope, userId, keyHash));
        }

        private void failIfUnavailable() {
            if (unavailable) {
                throw new IllegalStateException("redis down");
            }
        }

        private String key(IdempotencyScope scope, long userId, String keyHash) {
            return scope.slug() + ":" + userId + ":" + keyHash;
        }
    }
}
