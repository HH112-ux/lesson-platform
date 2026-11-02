package com.lp.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Verifies how {@link TrustedIdentityFilter} resolves, exposes and clears trusted identity.
 *
 * @author Caspian
 * @since 0.1.0
 */
class TrustedIdentityFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TrustedIdentityFilter filter = new TrustedIdentityFilter(objectMapper);

    /**
     * Rejects a protected request that carries no identity header.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsRequestWithoutIdentityHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("downstream must not be called");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":40101");
        assertThat(response.getContentAsString()).contains("\"traceId\":");
    }

    /**
     * Rejects a non numeric identity header instead of guessing a user id.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMalformedIdentityHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        request.addHeader(IdentityHeaders.USER_ID, "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("40101");
    }

    /**
     * Binds identity for the downstream call and clears it afterwards.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void bindsIdentityAndClearsItAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(IdentityHeaders.USER_ROLES, "STUDENT, TEACHER");
        request.addHeader(IdentityHeaders.USER_SOURCE, IdentityHeaders.SOURCE_WEB);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<UserContext> captured = new AtomicReference<>();

        FilterChain chain = (req, res) -> captured.set(UserContext.get());
        filter.doFilter(request, response, chain);

        UserContext context = captured.get();
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo(42L);
        assertThat(context.getRoles()).containsExactly("STUDENT", "TEACHER");
        assertThat(context.getSource()).isEqualTo(IdentityHeaders.SOURCE_WEB);
        assertThat(UserContext.get()).as("context must be cleared").isNull();
    }

    /**
     * Lets whitelisted paths through without identity.
     *
     * @throws Exception when the filter chain fails
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void passesWhitelistedPathWithoutIdentity() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/auth/miniapp-login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(UserContext.get()).isNull();
    }
}
