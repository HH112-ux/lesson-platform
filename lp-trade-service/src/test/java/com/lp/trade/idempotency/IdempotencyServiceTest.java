package com.lp.trade.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the replay, conflict and degradation behaviour of {@link IdempotencyService}.
 *
 * @author Caspian
 * @since 0.4.0
 */
class IdempotencyServiceTest {

    private static final String TRACE_ID = "trace-1";

    private final FakeStore store = new FakeStore();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void replaysTheExactResponseBodyIncludingTheEnvelope() {
        AtomicInteger calls = new AtomicInteger();

        IdempotencyOutcome<String> first = createOrder(calls);
        IdempotencyOutcome<String> second = createOrder(calls);

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        // Replay is a byte for byte copy, not a rebuilt envelope.
        assertEquals(first.value(), second.value());
        assertTrue(first.value().contains("\"code\":0"));
        assertTrue(first.value().contains("\"traceId\":\"trace-1\""));
        assertTrue(first.value().contains("\"id\":\"order-1\""));
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
                                        TRACE_ID,
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
                                TRACE_ID,
                                () -> {
                                    attempts.incrementAndGet();
                                    throw new BizException(ErrorCode.PARAM_INVALID, "bad reason");
                                }));

        IdempotencyOutcome<String> recovered =
                service.execute(
                        IdempotencyScope.REFUND_APPLY,
                        7L,
                        "key-1",
                        "digest",
                        TRACE_ID,
                        () -> {
                            attempts.incrementAndGet();
                            return new Payload("refund-1", 100);
                        });

        assertEquals(2, attempts.get());
        assertFalse(recovered.replayed());
        assertTrue(recovered.value().contains("\"id\":\"refund-1\""));
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
                                        TRACE_ID,
                                        () -> {
                                            firstStarted.countDown();
                                            sleep(200L);
                                            calls.incrementAndGet();
                                            return new Payload("pay-1", 300);
                                        }));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

        IdempotencyOutcome<String> second =
                service.execute(
                        IdempotencyScope.PAYMENT_INITIATE,
                        7L,
                        "key-1",
                        "digest",
                        TRACE_ID,
                        () -> {
                            calls.incrementAndGet();
                            return new Payload("pay-2", 300);
                        });

        assertEquals(first.get().value(), second.value());
        assertTrue(second.replayed());
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotRerunTheActionWhenTheResultLandsBetweenLookupAndLease() throws Exception {
        CountDownLatch claiming = new CountDownLatch(1);
        CountDownLatch ownerFinished = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        GatedStore gated = new GatedStore(claiming, ownerFinished);
        IdempotencyService gatedService =
                new IdempotencyService(gated, objectMapper, meterRegistry, 5_000L, 20L);

        var waiter =
                executor.submit(
                        () ->
                                gatedService.execute(
                                        IdempotencyScope.PAYMENT_INITIATE,
                                        7L,
                                        "key-1",
                                        "digest",
                                        TRACE_ID,
                                        () -> {
                                            calls.incrementAndGet();
                                            return new Payload("pay-2", 300);
                                        }));

        assertTrue(claiming.await(2, TimeUnit.SECONDS));
        gated.completeAsOwner(
                objectMapper.writeValueAsString(Result.ok(new Payload("pay-1", 300), TRACE_ID)));
        ownerFinished.countDown();

        IdempotencyOutcome<String> outcome = waiter.get(5, TimeUnit.SECONDS);

        assertTrue(outcome.replayed());
        assertTrue(outcome.value().contains("\"id\":\"pay-1\""));
        assertEquals(0, calls.get());
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
                                        TRACE_ID,
                                        () -> new Payload("never", 0)));

        assertEquals(ErrorCode.IDEMPOTENCY_IN_PROGRESS.getCode(), exception.getCode());
    }

    @Test
    void fallsBackToTheActionWhenTheStoreIsUnavailable() {
        store.unavailable = true;
        AtomicInteger calls = new AtomicInteger();

        IdempotencyOutcome<String> outcome = createOrder(calls);

        assertFalse(outcome.replayed());
        assertTrue(outcome.value().contains("\"id\":\"order-1\""));
        assertEquals(1, calls.get());
        assertEquals(
                1.0,
                meterRegistry
                        .get("lp.trade.idempotency.degraded")
                        .tag("scope", "order-create")
                        .counter()
                        .count());
    }

    private IdempotencyOutcome<String> createOrder(AtomicInteger calls) {
        return service.execute(
                IdempotencyScope.ORDER_CREATE,
                7L,
                "key-1",
                IdempotencyDigests.order(1L, null),
                TRACE_ID,
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
            String key = key(scope, userId, keyHash);
            // Mirrors the store contract: a completed key can never be leased again.
            if (results.containsKey(key)) {
                return false;
            }
            return leases.add(key);
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

    /**
     * Store that parks the waiter inside its second lease attempt until the first owner finished.
     *
     * <p>The first owner holds the lease and stores its result before releasing it. The second
     * {@code acquireLease} call signals {@code claiming}, waits for the test to complete the owner,
     * and then grants the lease even though a result is already stored. That reproduces the
     * save-then-release window deterministically, without sleeping, and isolates the service level
     * recheck because this store deliberately ignores the stored result when granting.
     *
     * @author Caspian
     * @since 0.4.0
     */
    static final class GatedStore implements IdempotencyStore {

        private static final String FIRST_OWNER = "first-request";

        private static final IdempotencyScope SCOPE = IdempotencyScope.PAYMENT_INITIATE;

        private static final long USER_ID = 7L;

        private static final String KEY_HASH = IdempotencyDigests.sha256("key-1");

        private final Map<String, StoredIdempotentResult> results = new ConcurrentHashMap<>();

        private final Map<String, String> leases = new ConcurrentHashMap<>();

        private final CountDownLatch claiming;

        private final CountDownLatch ownerFinished;

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        GatedStore(CountDownLatch claiming, CountDownLatch ownerFinished) {
            this.claiming = claiming;
            this.ownerFinished = ownerFinished;
            leases.put(key(SCOPE, USER_ID, KEY_HASH), FIRST_OWNER);
        }

        @Override
        public Optional<StoredIdempotentResult> findResult(
                IdempotencyScope scope, long userId, String keyHash) {
            return Optional.ofNullable(results.get(key(scope, userId, keyHash)));
        }

        @Override
        public boolean acquireLease(
                IdempotencyScope scope,
                long userId,
                String keyHash,
                String ownerToken,
                Duration ttl) {
            String key = key(scope, userId, keyHash);
            AtomicInteger attemptsForKey =
                    attempts.computeIfAbsent(key, ignored -> new AtomicInteger());
            if (attemptsForKey.incrementAndGet() == 1) {
                return false;
            }
            claiming.countDown();
            await(ownerFinished);
            return leases.putIfAbsent(key, ownerToken) == null;
        }

        @Override
        public void saveResult(
                IdempotencyScope scope,
                long userId,
                String keyHash,
                StoredIdempotentResult result,
                Duration ttl) {
            results.put(key(scope, userId, keyHash), result);
        }

        @Override
        public void releaseLease(
                IdempotencyScope scope, long userId, String keyHash, String ownerToken) {
            leases.remove(key(scope, userId, keyHash), ownerToken);
        }

        void completeAsOwner(String body) {
            saveResult(
                    SCOPE,
                    USER_ID,
                    KEY_HASH,
                    new StoredIdempotentResult("digest", body),
                    Duration.ofMinutes(30));
            releaseLease(SCOPE, USER_ID, KEY_HASH, FIRST_OWNER);
        }

        private String key(IdempotencyScope scope, long userId, String keyHash) {
            return scope.slug() + ":" + userId + ":" + keyHash;
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the first owner did not finish in time");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "interrupted while waiting for the owner", exception);
            }
        }
    }
}
