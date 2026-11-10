package com.lp.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.common.web.TrustedClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limits {@code POST /api/v1/auth/miniapp-login} per real client IP and per device.
 *
 * <p>The IP dimension is a NAT tolerant backstop; the device dimension applies five requests per
 * minute to one client identified by the optional {@code X-Device-Id} header. Without that header
 * only the IP dimension applies, never a forgeable user agent. Counters live in Redis.
 *
 * <p>Redis is non-critical here: an outage logs a WARN, increments {@code
 * lp.auth.miniapp_login.rate_limit.degraded}, and lets the request continue. Protection is lost
 * until recovery, counters then start from zero, and the rejected counter is tagged by dimension so
 * NAT false positives stay observable.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MiniappLoginRateLimitFilter extends OncePerRequestFilter {

    static final String PATH = "/api/v1/auth/miniapp-login";

    static final String HEADER_DEVICE_ID = "X-Device-Id";

    static final String METRIC_DEGRADED = "lp.auth.miniapp_login.rate_limit.degraded";

    static final String METRIC_REJECTED = "lp.auth.miniapp_login.rate_limit.rejected";

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('INCR', KEYS[1]) "
                            + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                            + "return current",
                    Long.class);

    private static final Logger log = LoggerFactory.getLogger(MiniappLoginRateLimitFilter.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final MeterRegistry meterRegistry;

    private final TrustedClientIpResolver clientIpResolver;

    private final int ipLimit;

    private final int deviceLimit;

    private final int windowSeconds;

    /**
     * Creates the filter and validates its configuration.
     *
     * @param redisTemplate shared string template
     * @param objectMapper serializer for the rejection envelope
     * @param meterRegistry registry for degraded and rejected counters
     * @param ipLimit allowed requests per client IP per window
     * @param deviceLimit allowed requests per IP and device per window
     * @param windowSeconds window length in seconds
     * @param trustedProxies comma separated CIDR blocks allowed to forward the client address
     * @throws IllegalStateException when a limit or the window is not positive
     * @author Caspian
     * @since 0.1.0
     */
    public MiniappLoginRateLimitFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${lp.auth.miniapp-login-rate-limit.ip-limit:30}") int ipLimit,
            @Value("${lp.auth.miniapp-login-rate-limit.device-limit:5}") int deviceLimit,
            @Value("${lp.auth.miniapp-login-rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${lp.auth.miniapp-login-rate-limit.trusted-proxies:127.0.0.1,::1}")
                    String trustedProxies) {
        if (ipLimit < 1 || deviceLimit < 1 || windowSeconds < 1) {
            throw new IllegalStateException(
                    "miniapp login rate limit values must be positive integers");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clientIpResolver =
                new TrustedClientIpResolver(Arrays.asList(trustedProxies.split(",")));
        this.ipLimit = ipLimit;
        this.deviceLimit = deviceLimit;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Applies both dimensions before the login request reaches the controller.
     *
     * @param request current request
     * @param response current response
     * @param filterChain remaining chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException when writing the rejection fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isTarget(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp =
                clientIpResolver.resolve(
                        request.getRemoteAddr(),
                        Collections.list(request.getHeaders("X-Forwarded-For")));
        long retryAfter = check("ip", "ip|" + clientIp, ipLimit);
        String deviceId = request.getHeader(HEADER_DEVICE_ID);
        if (retryAfter == 0 && deviceId != null && !deviceId.isBlank()) {
            retryAfter = check("device", "device|" + clientIp + "|" + deviceId.trim(), deviceLimit);
        }
        if (retryAfter > 0) {
            reject(request, response, retryAfter);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTarget(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && PATH.equals(request.getRequestURI());
    }

    // Fail-open on Redis errors: login is the critical path, so a cache outage must not block it.
    private long check(String dimension, String rawKey, int limit) {
        String key = "lp:auth:miniapp-login:rl:" + sha256(rawKey);
        Long count;
        try {
            count =
                    redisTemplate.execute(
                            INCREMENT_SCRIPT, List.of(key), Integer.toString(windowSeconds));
        } catch (DataAccessException ex) {
            return degraded(dimension, ex.getMessage());
        }
        if (count == null) {
            return degraded(dimension, "empty result");
        }
        if (count <= limit) {
            return 0;
        }
        meterRegistry.counter(METRIC_REJECTED, "dimension", dimension).increment();
        Long ttl;
        try {
            ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (DataAccessException ex) {
            return windowSeconds;
        }
        return ttl == null || ttl <= 0 ? windowSeconds : ttl;
    }

    private long degraded(String dimension, String cause) {
        meterRegistry.counter(METRIC_DEGRADED, "dimension", dimension).increment();
        log.warn("miniapp login rate limit degraded dimension={} cause={}", dimension, cause);
        return 0;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body =
                Result.fail(
                        ErrorCode.RATE_LIMITED.getCode(),
                        ErrorCode.RATE_LIMITED.getDefaultMessage(),
                        TraceIds.resolve(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
