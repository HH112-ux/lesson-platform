package com.lp.gateway.filter;

import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TrustedClientIpResolver;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies a per-instance token bucket to {@code /api/v1/**} to protect downstream services.
 *
 * <p>The bucket is keyed by the authenticated user id injected by {@link JwtAuthFilter}, falling
 * back to the trusted client IP for anonymous calls. The bucket starts full, so the configured
 * per-minute limit is also the burst capacity. State is in memory: the effective cluster capacity
 * is {@code limit-per-minute x replica count}, and a restart resets every bucket. {@code
 * /internal/**} is not routed through the gateway and every non {@code /api/v1/} path is skipped,
 * so service to service traffic and actuator endpoints are never limited here.
 *
 * <p>A rejection returns HTTP {@code 429}, {@code Retry-After} and the unified {@code Result}
 * envelope with code {@code 42901}; it never returns a bare status.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    /** Runs after identity verification so the user id is available. */
    public static final int ORDER = -80;

    /** Hard cap on tracked buckets; idle entries are swept before the cap is exceeded. */
    static final int MAX_BUCKETS = 10_000;

    private static final long IDLE_NANOS = Duration.ofMinutes(5).toNanos();

    private static final double NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    private final TrustedClientIpResolver clientIpResolver;

    private final boolean enabled;

    private final int limitPerMinute;

    /**
     * Creates the filter.
     *
     * @param objectMapper serializer for the rejection envelope
     * @param enabled whether gateway rate limiting is active
     * @param limitPerMinute allowed requests per key per minute, also the burst capacity
     * @param trustedProxies comma separated CIDR blocks allowed to forward the client address
     * @throws IllegalStateException when the limit is not a positive integer
     * @author Caspian
     * @since 0.1.0
     */
    public GatewayRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${lp.rate-limit.gateway.enabled:true}") boolean enabled,
            @Value("${lp.rate-limit.gateway.limit-per-minute:300}") int limitPerMinute,
            @Value("${lp.rate-limit.gateway.trusted-proxies:127.0.0.1,::1}")
                    String trustedProxies) {
        if (limitPerMinute < 1) {
            throw new IllegalStateException(
                    "lp.rate-limit.gateway.limit-per-minute must be a positive integer");
        }
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.clientIpResolver =
                new TrustedClientIpResolver(Arrays.asList(trustedProxies.split(",")));
    }

    /**
     * Consumes one token and rejects with {@code 429} when the bucket is empty.
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
        if (!enabled || !path.startsWith("/api/v1/")) {
            return chain.filter(exchange);
        }
        long retryAfter = bucket(keyOf(exchange)).tryAcquire();
        if (retryAfter > 0) {
            return reject(exchange, retryAfter);
        }
        return chain.filter(exchange);
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

    private String keyOf(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(IdentityHeaders.USER_ID);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        String remoteAddr =
                exchange.getRequest().getRemoteAddress() == null
                        ? null
                        : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String clientIp =
                clientIpResolver.resolve(
                        remoteAddr, exchange.getRequest().getHeaders().get("X-Forwarded-For"));
        return "ip:" + clientIp;
    }

    private Bucket bucket(String key) {
        if (buckets.size() >= MAX_BUCKETS) {
            long cutoff = System.nanoTime() - IDLE_NANOS;
            buckets.entrySet().removeIf(entry -> entry.getValue().idleSince() < cutoff);
        }
        return buckets.computeIfAbsent(key, ignored -> new Bucket(limitPerMinute));
    }

    private Mono<Void> reject(ServerWebExchange exchange, long retryAfter) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getRequest().getHeaders().getFirst(IdentityHeaders.REQUEST_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Result<Void> body =
                Result.fail(
                        ErrorCode.RATE_LIMITED.getCode(),
                        ErrorCode.RATE_LIMITED.getDefaultMessage(),
                        traceId);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (JacksonException ex) {
            return exchange.getResponse().setComplete();
        }
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(payload)));
    }

    /** Token bucket for one key, refilled continuously at the configured per-minute rate. */
    private static final class Bucket {

        private final double capacity;

        private final double tokensPerNano;

        private double tokens;

        private long lastRefillNanos;

        private volatile long lastUsedNanos;

        private Bucket(int limitPerMinute) {
            this.capacity = limitPerMinute;
            this.tokensPerNano = limitPerMinute / NANOS_PER_MINUTE;
            this.tokens = limitPerMinute;
            this.lastRefillNanos = System.nanoTime();
            this.lastUsedNanos = this.lastRefillNanos;
        }

        private synchronized long tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
            lastUsedNanos = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            double seconds = (1 - tokens) / tokensPerNano / 1_000_000_000d;
            return Math.max(1, (long) Math.ceil(seconds));
        }

        private long idleSince() {
            return lastUsedNanos;
        }
    }
}
