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
import java.util.List;
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
 * <p>It runs after {@link HeaderSanitizeFilter}, so any client supplied identity header is already
 * gone. Public paths pass through without identity. Depending on {@code lp.identity.enforce-mode},
 * a missing or invalid token is logged only ({@code obs}), logged and forwarded ({@code soft}), or
 * rejected with {@code 40101}/{@code 40102} ({@code hard}). With a valid token the filter injects
 * {@code X-User-Id}, {@code X-User-Roles}, {@code X-User-Permissions}, {@code X-User-Source} and
 * {@code X-Request-Id}.
 *
 * <p>Requests under {@code /api/v1/admin/**} additionally require the {@code ADMIN} or {@code OPS}
 * role; an authenticated user without either role gets HTTP {@code 403} with code {@code 40301}.
 * This is the coarse "may enter the console" gate; per endpoint permission checks stay in the admin
 * service.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** Runs after the header sanitizer and before route filters. */
    public static final int ORDER = -90;

    /** Roles allowed to call the operations console API under {@code /api/v1/admin}. */
    public static final List<String> ADMIN_CONSOLE_ROLES = List.of("ADMIN", "OPS");

    /** Error code returned when an authenticated user lacks the required role. */
    public static final int CODE_FORBIDDEN = 40301;

    /** Prefix of the operational console API, guarded by the coarse role check. */
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

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
    public JwtAuthFilter(
            IdentityProperties properties, JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Verifies the token, then forwards with injected identity headers.
     *
     * @param exchange current exchange
     * @param chain remaining filter chain
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
        boolean optional = isOptionalAuth(path);

        String token =
                bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            if (optional) {
                return chain.filter(exchange);
            }
            return onFailure(exchange, chain, 40101, "missing credentials or identity", "absent");
        }

        VerifiedUser user;
        try {
            user = jwtVerifier.verify(token);
        } catch (ExpiredJwtException ex) {
            if (optional) {
                return chain.filter(exchange);
            }
            return onFailure(exchange, chain, 40102, "credential expired", "expired");
        } catch (JwtException | IllegalArgumentException ex) {
            if (optional) {
                return chain.filter(exchange);
            }
            return onFailure(exchange, chain, 40101, "missing credentials or identity", "invalid");
        }

        if (requiresConsoleRole(path) && !hasAnyRole(user.getRoles())) {
            return reject(
                    exchange,
                    chain,
                    HttpStatus.FORBIDDEN,
                    CODE_FORBIDDEN,
                    "permission denied",
                    "role");
        }

        if (!properties.isInjectEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request =
                exchange.getRequest()
                        .mutate()
                        .headers(
                                headers -> {
                                    headers.set(
                                            IdentityHeaders.USER_ID,
                                            Long.toString(user.getUserId()));
                                    headers.set(
                                            IdentityHeaders.USER_ROLES,
                                            String.join(",", user.getRoles()));
                                    headers.set(
                                            IdentityHeaders.USER_PERMISSIONS,
                                            String.join(",", user.getPermissions()));
                                    headers.set(IdentityHeaders.USER_SOURCE, sourceOf(path));
                                    if (headers.getFirst(IdentityHeaders.REQUEST_ID) == null) {
                                        headers.set(
                                                IdentityHeaders.REQUEST_ID,
                                                UUID.randomUUID().toString());
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
     * @param chain remaining chain
     * @param code error code to return in hard mode
     * @param message error message to return in hard mode
     * @param reason short reason for the log line, never the token itself
     * @return rejection response in hard mode, otherwise the downstream chain
     * @author Caspian
     * @since 0.1.0
     */
    private Mono<Void> onFailure(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            int code,
            String message,
            String reason) {
        if (!"hard".equalsIgnoreCase(properties.getEnforceMode())) {
            log.warn(
                    "token rejected mode={} reason={} path={}",
                    properties.getEnforceMode(),
                    reason,
                    exchange.getRequest().getURI().getPath());
            return chain.filter(exchange);
        }
        return reject(exchange, chain, HttpStatus.UNAUTHORIZED, code, message, reason);
    }

    /**
     * Writes the unified error envelope with the given HTTP status.
     *
     * @param exchange current exchange
     * @param chain remaining chain, unused because the request is terminated
     * @param status HTTP status to return
     * @param code business error code
     * @param message error message
     * @param reason short reason for the log line
     * @return completion signal of the rejection response
     * @author Caspian
     * @since 0.1.0
     */
    private Mono<Void> reject(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            HttpStatus status,
            int code,
            String message,
            String reason) {
        log.warn(
                "request rejected status={} code={} reason={} path={}",
                status.value(),
                code,
                reason,
                exchange.getRequest().getURI().getPath());
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
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(payload)));
    }

    /**
     * Tells whether the path belongs to the operations console API.
     *
     * @param path request path
     * @return {@code true} for {@code /api/v1/admin} and its sub paths
     * @author Caspian
     * @since 0.1.0
     */
    private static boolean requiresConsoleRole(String path) {
        return path.equals("/api/v1/admin") || path.startsWith(ADMIN_PATH_PREFIX);
    }

    /**
     * Tells whether the user holds a console role.
     *
     * @param roles verified role codes
     * @return {@code true} when at least one role is in {@link #ADMIN_CONSOLE_ROLES}
     * @author Caspian
     * @since 0.1.0
     */
    private static boolean hasAnyRole(List<String> roles) {
        for (String role : roles) {
            if (ADMIN_CONSOLE_ROLES.contains(role)) {
                return true;
            }
        }
        return false;
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
     * Tells whether a path accepts anonymous callers while still honouring a valid token.
     *
     * @param path request path
     * @return {@code true} when an optional-auth pattern matches
     * @author Caspian
     * @since 0.1.0
     */
    private boolean isOptionalAuth(String path) {
        for (String pattern : properties.getOptionalAuth()) {
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
