package com.lp.common.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the trusted identity headers into {@link UserContext} for business services.
 *
 * <p>Whitelisted paths pass through without identity. Every other path must carry a numeric
 * {@code X-User-Id}; otherwise the filter responds with HTTP {@code 401} and error code
 * {@code 40101}. Headers produced by a client are already stripped at the gateway, so this
 * filter trusts only gateway injected values.</p>
 *
 * <p>Registration in a servlet service:</p>
 * <pre>{@code
 * FilterRegistrationBean<TrustedIdentityFilter> bean = new FilterRegistrationBean<>();
 * bean.setFilter(new TrustedIdentityFilter(objectMapper));
 * bean.setOrder(Ordered.LOWEST_PRECEDENCE);
 * }</pre>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class TrustedIdentityFilter extends OncePerRequestFilter {

    /** Paths that pass through without identity: login, callbacks, health and error pages. */
    public static final List<String> DEFAULT_WHITELIST =
            List.of("/api/v1/auth/**", "/actuator/**", "/error");

    /** Error code returned when the trusted identity header is missing or malformed. */
    public static final int CODE_UNAUTHENTICATED = 40101;

    private static final String UNAUTHENTICATED_MESSAGE = "missing credentials or identity";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final ObjectMapper objectMapper;

    private final List<String> whitelistPaths;

    /**
     * Creates a filter using {@link #DEFAULT_WHITELIST}.
     *
     * @param objectMapper serializer used to write the error envelope
     * @author Caspian
     * @since 0.1.0
     */
    public TrustedIdentityFilter(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_WHITELIST);
    }

    /**
     * Creates a filter with an explicit whitelist.
     *
     * @param objectMapper    serializer used to write the error envelope
     * @param whitelistPaths  Ant style path patterns that skip identity resolution,
     *                        {@code null} treated as empty
     * @author Caspian
     * @since 0.1.0
     */
    public TrustedIdentityFilter(ObjectMapper objectMapper, List<String> whitelistPaths) {
        this.objectMapper = objectMapper;
        this.whitelistPaths = whitelistPaths == null ? List.of() : List.copyOf(whitelistPaths);
    }

    /**
     * Resolves identity, delegates the request, and always clears the bound context.
     *
     * @param request     incoming request
     * @param response    outgoing response
     * @param filterChain remaining filter chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException      when writing the response fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isWhitelisted(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(request.getHeader(IdentityHeaders.USER_ID));
        } catch (NumberFormatException ex) {
            writeUnauthorized(request, response);
            return;
        }

        UserContext context = new UserContext(userId,
                splitRoles(request.getHeader(IdentityHeaders.USER_ROLES)),
                request.getHeader(IdentityHeaders.USER_SOURCE),
                request.getHeader(IdentityHeaders.TENANT_ID));
        UserContext.set(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * Tells whether the path is exempt from identity resolution.
     *
     * @param path request URI
     * @return {@code true} when the path matches a whitelist pattern
     * @author Caspian
     * @since 0.1.0
     */
    private boolean isWhitelisted(String path) {
        for (String pattern : whitelistPaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits the comma separated role header, ignoring blank elements.
     *
     * @param rolesHeader raw header value, may be {@code null}
     * @return immutable role list, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    private List<String> splitRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (String role : rolesHeader.split(",")) {
            if (!role.isBlank()) {
                roles.add(role.trim());
            }
        }
        return roles;
    }

    /**
     * Writes the {@code 401} response with error code {@code 40101}.
     *
     * @param request  current request, used for the trace id
     * @param response response to write
     * @throws IOException when writing fails
     * @author Caspian
     * @since 0.1.0
     */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> body = Result.fail(CODE_UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE,
                TraceIds.resolve(request));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
