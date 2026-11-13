package com.lp.common.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies that {@link InternalTokenFilter} protects the internal API prefix only.
 *
 * @author Caspian
 * @since 0.1.0
 */
class InternalTokenFilterTest {

    private static final String TOKEN = "local-internal-token";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN, objectMapper);

    /**
     * Rejects an internal call that carries no token header.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMissingToken() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/v1/entitlement-outbox/failed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {
                    throw new AssertionError("downstream must not be called");
                });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":40101");
        assertThat(response.getContentAsString()).contains("\"traceId\":");
    }

    /**
     * Rejects an internal call whose token does not match the configured secret.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsWrongToken() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/v1/entitlements");
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, "another-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("40101");
    }

    /**
     * Delegates an internal call that carries the configured secret.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void acceptsMatchingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/courses");
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Ignores public routes so their own authentication keeps working.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void ignoresPublicRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Refuses a blank secret so a misconfigured service cannot start unprotected.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsBlankToken() {
        assertThatThrownBy(() -> new InternalTokenFilter("  ", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lp.internal.token");
    }
}
