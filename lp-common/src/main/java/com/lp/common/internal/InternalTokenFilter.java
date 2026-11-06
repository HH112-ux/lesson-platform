package com.lp.common.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects service-to-service calls that do not carry the shared internal token.
 *
 * <p>Only {@code /internal/**} requests are inspected; public routes keep their existing auth. The
 * expected value comes from {@code lp.internal.token}, which is bound to the {@code
 * LP_INTERNAL_TOKEN} environment variable without a default, so a missing secret fails startup
 * instead of silently disabling the check. Comparison is constant time to avoid leaking the token
 * through response timing.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    /** Error code returned when the internal token is missing or wrong. */
    public static final int CODE_UNAUTHENTICATED = 40101;

    private static final String INTERNAL_PREFIX = "/internal/";

    private static final String UNAUTHENTICATED_MESSAGE = "invalid internal token";

    private final byte[] expectedToken;

    private final ObjectMapper objectMapper;

    /**
     * Creates the filter with the configured shared secret.
     *
     * @param expectedToken configured token, must not be blank
     * @param objectMapper serializer used to write the error envelope
     * @throws IllegalArgumentException when the token is blank, so misconfiguration fails fast
     * @author Caspian
     * @since 0.1.0
     */
    public InternalTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("lp.internal.token must not be blank");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Skips every request that does not target the internal API.
     *
     * @param request incoming request
     * @return {@code true} when the path is not under {@code /internal/}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    /**
     * Validates the token header and delegates accepted requests.
     *
     * @param request incoming request
     * @param response outgoing response
     * @param filterChain remaining filter chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException when writing the response fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(InternalHeaders.INTERNAL_TOKEN);
        if (provided == null
                || !MessageDigest.isEqual(
                        expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            writeUnauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> body =
                Result.fail(
                        CODE_UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE, TraceIds.resolve(request));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
