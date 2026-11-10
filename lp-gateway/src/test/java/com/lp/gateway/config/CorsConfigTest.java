package com.lp.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

/** Verifies the browser origin policy and that preflight requests never reach the gateway chain. */
class CorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private static final String API_URL = "http://127.0.0.1:8280/api/v1/orders";

    @Test
    void answersPreflightWithoutReachingDownstreamFilters() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.options(API_URL)
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "authorization,content-type,idempotency-key,x-request-id")
                                .build());
        AtomicBoolean reachedDownstream = new AtomicBoolean(false);

        filter().filter(
                        exchange,
                        ignored -> {
                            reachedDownstream.set(true);
                            return Mono.empty();
                        })
                .block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(exchange.getResponse().getStatusCode())
                .satisfiesAnyOf(
                        status -> assertThat(status).isNull(),
                        status -> assertThat(status.is2xxSuccessful()).isTrue());
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("Idempotency-Key")
                .containsIgnoringCase("X-Request-Id")
                .containsIgnoringCase("Authorization");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        assertThat(reachedDownstream).isFalse();
    }

    @Test
    void rejectsPreflightFromUnknownOrigin() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.options(API_URL)
                                .header(HttpHeaders.ORIGIN, "https://evil.example")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .build());

        filter().filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        exchange.getResponse()
                                .getHeaders()
                                .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isNull();
    }

    @Test
    void rejectsPreflightThatAsksForAnUnlistedHeader() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.options(API_URL)
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-not-allowed")
                                .build());

        filter().filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsWildcardOriginBecauseCredentialsAreAllowed() {
        assertThatIllegalStateException()
                .isThrownBy(() -> CorsConfig.buildConfiguration(List.of("*"), true, 3600))
                .withMessageContaining("'*'");
    }

    @Test
    void failsOutsideDevWhenNoOriginIsConfigured() {
        assertThatIllegalStateException()
                .isThrownBy(() -> CorsConfig.buildConfiguration(List.of(), false, 3600))
                .withMessageContaining("must not be empty outside the dev profile");
    }

    @Test
    void usesLocalhostOnlyWhenDevProfileIsActive() {
        CorsConfiguration configuration = CorsConfig.buildConfiguration(List.of(), true, 3600);

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://localhost:5174");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    private CorsWebFilter filter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
                "/**", CorsConfig.buildConfiguration(List.of(ALLOWED_ORIGIN), false, 3600));
        return new CorsWebFilter(source);
    }
}
