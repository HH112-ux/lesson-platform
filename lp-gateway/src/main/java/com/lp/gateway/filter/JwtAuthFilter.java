package com.lp.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.gateway.config.IdentityProperties;
import com.lp.gateway.security.JwtVerifier;
import com.lp.gateway.security.VerifiedUser;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies the access token and injects trusted identity headers.
 *
 * <p>It runs after {@link HeaderSanitizeFilter}, so any client supplied identity header is
 * already gone. Public paths pass through without identity. Depending on
 * {@code lp.identity.enforce-mode}, a missing or invalid token is logged only ({@code obs}),
 * logged and forwarded ({@code soft}), or rejected with {@code 40101}/{@code 40102}
 * ({@code hard}). With a valid token the filter injects {@code X-User-Id},
 * {@code X-User-Roles}, {@code X-User-Source} and {@code X-Request-Id}.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** Runs after the header sanitizer and before route filters. */
    public static final int ORDER = -90;

    /** Prefix of the authorization header value. */
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final IdentityProperties properties;

    private final JwtVerifier jwtVerifier;

    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Creates the filter.
     *
     * @param properties identity settings
     * @param jwtVerifier token verifier
     * @param objectMapper serializer for error envelopes
     * @author Caspian
     * @since 0.1.0
     */
    public JwtAuthFilter(IdentityProperties properties, JwtVerifier jwtVerifier,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Verifies the token, then forwards with injected identity headers.
     *
     * @param exchange current exchange
     * @param chain    remaining filter chain
     * @return completion signal of the downstream chain or the rejection response
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = bearerToken(exchange.getRequest().getHeaders().getFirst(
                HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return onFailure(exchange, chain, 40101, "missing credentials or identity", "absent");
        }

        VerifiedUser user;
        try {
            user = jwtVerifier.verify(token);
        } catch (ExpiredJwtException ex) {
            return onFailure(exchange, chain, 40102, "credential expired", "expired");
        } catch (JwtException | IllegalArgumentException ex) {
            return onFailure(exchange, chain, 40101, "missing credentials or identity", "invalid");
        }

        if (!properties.isInjectEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(IdentityHeaders.USER_ID, Long.toString(user.getUserId()));
                    headers.set(IdentityHeaders.USER_ROLES, String.join(",", user.getRoles()));
                    headers.set(IdentityHeaders.USER_SOURCE, sourceOf(path));
                    if (headers.getFirst(IdentityHeaders.REQUEST_ID) == null) {
                        headers.set(IdentityHeaders.REQUEST_ID, UUID.randomUUID().toString());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * Returns the filter order; lower values run first.
     *
     * @return {@link #ORDER}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Handles an unverifiable token according to the enforcement mode.
     *
     * @param exchange current exchange
     * @param chain    remaining chain
     * @param code     error code to return in hard mode
     * @param message  error message to return in hard mode
     * @param reason   short reason for the log line, never the token itself
     * @return rejection response in hard mode, otherwise the downstream chain
     * @author Caspian
     * @since 0.1.0
     */
    private Mono<Void> onFailure(ServerWebExchange exchange, GatewayFilterChain chain, int code,
                                 String message, String reason) {
        if (!"hard".equalsIgnoreCase(properties.getEnforceMode())) {
            log.warn("token rejected mode={} reason={} path={}", properties.getEnforceMode(),
                    reason, exchange.getRequest().getURI().getPath());
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(IdentityHeaders.REQUEST_ID);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Result<Void> body = Result.fail(code, message, traceId);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(payload)));
    }

    /**
     * Extracts the token from a bearer header.
     *
     * @param authorization raw header value, may be {@code null}
     * @return token text, or {@code null} when the scheme is absent or blank
     * @author Caspian
     * @since 0.1.0
     */
    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Tells whether the path never requires identity.
     *
     * @param path request path
     * @return {@code true} when a whitelist pattern matches
     * @author Caspian
     * @since 0.1.0
     */
    private boolean isWhitelisted(String path) {
        for (String pattern : properties.getWhitelist()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Derives the source platform from the entry segment, never from a client header.
     *
     * @param path request path
     * @return miniapp under {@code /mp/**}, admin under {@code /api/v1/admin/**}, else web
     * @author Caspian
     * @since 0.1.0
     */
    private static String sourceOf(String path) {
        if (path.startsWith("/mp/")) {
            return IdentityHeaders.SOURCE_MINIAPP;
        }
        if (path.startsWith("/api/v1/admin/")) {
            return IdentityHeaders.SOURCE_ADMIN;
        }
        return IdentityHeaders.SOURCE_WEB;
    }
}
