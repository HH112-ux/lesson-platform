package com.lp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies that {@link HeaderSanitizeFilter} strips client identity before the chain continues.
 *
 * @author Caspian
 * @since 0.1.0
 */
class HeaderSanitizeFilterTest {

    /**
     * Forwards the request with all client identity headers removed and other headers intact.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void stripsIdentityHeadersForDownstream() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/course/lessons")
                .header("X-User-Id", "999")
                .header("x-user-roles", "ADMIN")
                .header("X-User-Phone", "[PHONE_1]")
                .header("Authorization", "Bearer token")
                .header("X-Request-Id", "req-1")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new HeaderSanitizeFilter().filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-User-Roles")).isNull();
        assertThat(headers.getFirst("X-User-Phone")).isNull();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("req-1");
        assertThat(forwarded.get().getRequest().getPath().value())
                .isEqualTo("/api/v1/course/lessons");
    }

    /**
     * Drops case variants and duplicate values of the same identity header.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void removesCaseVariantsAndDuplicates() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/user/profile?tab=orders")
                .header("X-User-Id", "999")
                .header("x-user-id", "1000")
                .header("X-User-Roles", "ADMIN")
                .header("X-User-Roles", "TEACHER")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new HeaderSanitizeFilter().filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get("X-User-Id")).isNull();
        assertThat(headers.get("X-User-Roles")).isNull();
        assertThat(forwarded.get().getRequest().getURI().getRawQuery()).isEqualTo("tab=orders");
    }
}
