package com.lp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.identity.IdentityHeaders;
import com.lp.gateway.config.IdentityProperties;
import com.lp.gateway.security.JwtVerifier;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Covers the gateway identity contract from {@code docs/engineering-plan.md} 6.2/6.4.
 *
 * @author Caspian
 * @since 0.1.0
 */
class JwtAuthFilterTest {

    private static final String ISSUER = "auth-service";

    private static KeyPair keyPair;

    private IdentityProperties properties;

    private JwtAuthFilter filter;

    /**
     * Generates a throwaway signing key pair shared by the verifier and test tokens.
     *
     * @throws Exception when the RSA provider is unavailable
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * Rebuilds the filter in hard mode before each case.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        properties = new IdentityProperties();
        properties.setEnforceMode("hard");
        filter = new JwtAuthFilter(properties,
                new JwtVerifier(keyPair.getPublic(), ISSUER), new ObjectMapper());
    }

    /**
     * Auth endpoints never require a token and never receive user headers.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void letsWhitelistedAuthPathThroughWithoutIdentity() {
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange("/api/v1/auth/login"), chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.exchange().getRequest().getHeaders().getFirst(IdentityHeaders.USER_ID))
                .isNull();
    }

    /**
     * A protected path without a token is rejected with {@code 40101}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMissingTokenInHardMode() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = exchange("/api/v1/courses");
        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(exchange)).contains("40101");
    }

    /**
     * An expired token is rejected with {@code 40102}, distinguishing it from a bad token.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsExpiredToken() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = exchangeWithToken("/api/v1/courses",
                token(7L, List.of("STUDENT"), Duration.ofMinutes(-1)));
        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(bodyOf(exchange)).contains("40102");
    }

    /**
     * A malformed token is rejected with {@code 40101}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMalformedToken() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = exchangeWithToken("/api/v1/courses", "not-a-jwt");
        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(bodyOf(exchange)).contains("40101");
    }

    /**
     * A valid token injects user id, roles, source and a generated request id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void injectsIdentityHeadersForValidToken() {
        CapturingChain chain = new CapturingChain();
        filter.filter(exchangeWithToken("/api/v1/courses",
                token(7L, List.of("STUDENT", "TEACHER"), Duration.ofMinutes(15))), chain).block();

        assertThat(chain.called).isTrue();
        HttpHeaders headers = chain.exchange().getRequest().getHeaders();
        assertThat(headers.getFirst(IdentityHeaders.USER_ID)).isEqualTo("7");
        assertThat(headers.getFirst(IdentityHeaders.USER_ROLES)).isEqualTo("STUDENT,TEACHER");
        assertThat(headers.getFirst(IdentityHeaders.USER_SOURCE)).isEqualTo("web");
        assertThat(headers.getFirst(IdentityHeaders.REQUEST_ID)).isNotBlank();
    }

    /**
     * The miniapp segment is the only way to obtain a miniapp source value.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void derivesMiniappSourceFromPath() {
        CapturingChain chain = new CapturingChain();
        filter.filter(exchangeWithToken("/mp/api/v1/courses",
                token(7L, List.of("STUDENT"), Duration.ofMinutes(15))), chain).block();

        assertThat(chain.exchange().getRequest().getHeaders()
                .getFirst(IdentityHeaders.USER_SOURCE)).isEqualTo("miniapp");
    }

    /**
     * Admin paths are marked as admin source.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void derivesAdminSourceFromPath() {
        CapturingChain chain = new CapturingChain();
        filter.filter(exchangeWithToken("/api/v1/admin/users",
                token(7L, List.of("OPS"), Duration.ofMinutes(15))), chain).block();

        assertThat(chain.exchange().getRequest().getHeaders()
                .getFirst(IdentityHeaders.USER_SOURCE)).isEqualTo("admin");
    }

    /**
     * Operating an existing client request id is preserved for correlation.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void preservesClientRequestId() {
        CapturingChain chain = new CapturingChain();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/courses")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(7L, List.of(), Duration.ofMinutes(15)))
                .header(IdentityHeaders.REQUEST_ID, "req-abc")
                .build();
        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(chain.exchange().getRequest().getHeaders()
                .getFirst(IdentityHeaders.REQUEST_ID)).isEqualTo("req-abc");
    }

    /**
     * Observation mode logs only, so rollout can start without rejecting traffic.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void passesThroughWithoutTokenInObservationMode() {
        properties.setEnforceMode("obs");
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange("/api/v1/courses"), chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.exchange().getRequest().getHeaders().getFirst(IdentityHeaders.USER_ID))
                .isNull();
    }

    /**
     * Builds an exchange for a path without authorization.
     *
     * @param path request path
     * @return mock exchange
     * @author Caspian
     * @since 0.1.0
     */
    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    /**
     * Builds an exchange carrying a bearer token.
     *
     * @param path  request path
     * @param token token text
     * @return mock exchange
     * @author Caspian
     * @since 0.1.0
     */
    private static MockServerWebExchange exchangeWithToken(String path, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

    /**
     * Signs a token with the test private key.
     *
     * @param userId user id
     * @param roles  role codes
     * @param ttl    lifetime, negative for an already expired token
     * @return compact JWT
     * @author Caspian
     * @since 0.1.0
     */
    private static String token(long userId, List<String> roles, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith((RSAPrivateKey) keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Reads the response body written by the filter.
     *
     * @param exchange mock exchange
     * @return response body text
     * @author Caspian
     * @since 0.1.0
     */
    private static String bodyOf(MockServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getResponse().getBody()).block();
        if (buffer == null) {
            return "";
        }
        String body = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        DataBufferUtils.release(buffer);
        return body;
    }

    /**
     * Records whether the downstream chain ran and the exchange it received.
     *
     * @author Caspian
     * @since 0.1.0
     */
    private static final class CapturingChain implements GatewayFilterChain {

        private final AtomicBoolean called = new AtomicBoolean(false);

        private final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        /**
         * Captures the exchange and completes.
         *
         * @param exchange filtered exchange
         * @return empty completion
         * @author Caspian
         * @since 0.1.0
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured.set(exchange);
            called.set(true);
            return Mono.empty();
        }

        /**
         * Returns the exchange received by the downstream chain.
         *
         * @return captured exchange
         * @author Caspian
         * @since 0.1.0
         */
        private ServerWebExchange exchange() {
            return captured.get();
        }
    }
}
