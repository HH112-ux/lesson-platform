package com.lp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Verifies the per-instance gateway token bucket and its rejection envelope. */
class GatewayRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsRequestAfterLimitWithUnifiedEnvelope() {
        GatewayRateLimitFilter filter = filter(2);
        assertThat(call(filter, exchange("/api/v1/courses", null)).block()).isNull();
        assertThat(call(filter, exchange("/api/v1/courses", null)).block()).isNull();

        AtomicBoolean reached = new AtomicBoolean(false);
        MockServerWebExchange rejected = exchange("/api/v1/courses", null);
        filter.filter(rejected, chain(reached)).block();

        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(rejected.getResponse().getBodyAsString().block()).contains("\"code\":42901");
        assertThat(reached).isFalse();
    }

    @Test
    void keepsSeparateBucketsPerUser() {
        GatewayRateLimitFilter filter = filter(1);
        assertThat(call(filter, exchange("/api/v1/orders", "1")).block()).isNull();

        AtomicBoolean reached = new AtomicBoolean(false);
        MockServerWebExchange rejected = exchange("/api/v1/orders", "1");
        filter.filter(rejected, chain(reached)).block();

        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(reached).isFalse();
        assertThat(call(filter, exchange("/api/v1/orders", "2")).block()).isNull();
    }

    @Test
    void skipsInternalActuatorAndNonApiPaths() {
        GatewayRateLimitFilter filter = filter(1);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(call(filter, exchange("/internal/v1/courses", null)).block()).isNull();
            assertThat(call(filter, exchange("/actuator/health", null)).block()).isNull();
        }
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new GatewayRateLimitFilter(objectMapper, true, 0, "127.0.0.1"));
    }

    private GatewayRateLimitFilter filter(int limit) {
        return new GatewayRateLimitFilter(objectMapper, true, limit, "127.0.0.1");
    }

    private Mono<Void> call(GatewayRateLimitFilter filter, MockServerWebExchange exchange) {
        return filter.filter(exchange, chain(new AtomicBoolean(false)));
    }

    private GatewayFilterChain chain(AtomicBoolean reached) {
        return exchange -> {
            reached.set(true);
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchange(String path, String userId) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.get(path)
                        .remoteAddress(new java.net.InetSocketAddress("198.51.100.10", 50000));
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        return MockServerWebExchange.from(request);
    }
}
