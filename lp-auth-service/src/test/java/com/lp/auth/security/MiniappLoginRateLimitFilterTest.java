package com.lp.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** Verifies the miniapp login limiter dimensions, fail-open behaviour and response shape. */
class MiniappLoginRateLimitFilterTest {

    private final CountingRedisTemplate redisTemplate = new CountingRedisTemplate();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void rejectsSixthRequestFromSameDeviceWithRetryAfter() throws Exception {
        MiniappLoginRateLimitFilter filter = filter(30, 5);
        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("198.51.100.10", "device-1", null), response, noopChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedController = new AtomicBoolean(false);
        filter.doFilter(
                request("198.51.100.10", "device-1", null),
                response,
                (req, res) -> reachedController.set(true));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("\"code\":42901");
        assertThat(reachedController).isFalse();
        assertThat(rejected("device")).isEqualTo(1);
    }

    @Test
    void appliesIpLimitWhenDeviceIdIsAbsent() throws Exception {
        MiniappLoginRateLimitFilter filter = filter(3, 5);
        for (int attempt = 0; attempt < 3; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("198.51.100.10", null, null), response, noopChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("198.51.100.10", null, null), response, noopChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(rejected("ip")).isEqualTo(1);
    }

    @Test
    void resolvesForwardedAddressOnlyFromTrustedPeer() throws Exception {
        MiniappLoginRateLimitFilter filter = filter(30, 5);
        filter.doFilter(
                request("127.0.0.1", null, "203.0.113.9"),
                new MockHttpServletResponse(),
                noopChain());
        filter.doFilter(
                request("198.51.100.10", null, "1.1.1.1"),
                new MockHttpServletResponse(),
                noopChain());
        filter.doFilter(
                request("198.51.100.10", null, null), new MockHttpServletResponse(), noopChain());

        assertThat(redisTemplate.keys).hasSize(3);
        assertThat(redisTemplate.keys.get(0)).isNotEqualTo(redisTemplate.keys.get(1));
        assertThat(redisTemplate.keys.get(2)).isEqualTo(redisTemplate.keys.get(1));
    }

    @Test
    void allowsRequestWhenRedisIsUnavailable() throws Exception {
        MiniappLoginRateLimitFilter filter = filter(5, 5);
        redisTemplate.fail = true;
        AtomicBoolean reachedController = new AtomicBoolean(false);

        filter.doFilter(
                request("198.51.100.10", "device-1", null),
                new MockHttpServletResponse(),
                (req, res) -> reachedController.set(true));

        assertThat(reachedController).isTrue();
        assertThat(
                        meterRegistry
                                .get(MiniappLoginRateLimitFilter.METRIC_DEGRADED)
                                .tag("dimension", "ip")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    private double rejected(String dimension) {
        return meterRegistry
                .get(MiniappLoginRateLimitFilter.METRIC_REJECTED)
                .tag("dimension", dimension)
                .counter()
                .count();
    }

    private MiniappLoginRateLimitFilter filter(int ipLimit, int deviceLimit) {
        return new MiniappLoginRateLimitFilter(
                redisTemplate,
                new ObjectMapper(),
                meterRegistry,
                ipLimit,
                deviceLimit,
                60,
                "127.0.0.1,::1");
    }

    private MockHttpServletRequest request(
            String remoteAddr, String deviceId, String forwardedFor) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", MiniappLoginRateLimitFilter.PATH);
        request.setRemoteAddr(remoteAddr);
        if (deviceId != null) {
            request.addHeader(MiniappLoginRateLimitFilter.HEADER_DEVICE_ID, deviceId);
        }
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private jakarta.servlet.FilterChain noopChain() {
        return (req, res) -> {};
    }

    /** Counts increments per key locally and can simulate a Redis outage. */
    private static final class CountingRedisTemplate extends StringRedisTemplate {

        private final Map<String, AtomicInteger> counters = new HashMap<>();

        private final List<String> keys = new ArrayList<>();

        private boolean fail;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (fail) {
                throw new RedisConnectionFailureException("redis down");
            }
            this.keys.addAll(keys);
            return (T)
                    Long.valueOf(
                            counters.computeIfAbsent(keys.get(0), key -> new AtomicInteger())
                                    .incrementAndGet());
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            return 42L;
        }
    }
}
