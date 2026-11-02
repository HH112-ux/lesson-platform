package com.lp.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Verifies the error codes and trace ids produced by {@link GlobalExceptionHandler}.
 *
 * @author Caspian
 * @since 0.1.0
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Keeps the business code and message of a {@link BizException}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void keepsBusinessExceptionCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        Result<Void> result = handler.handleBiz(
                new BizException(ErrorCode.PARAM_INVALID), request);

        assertThat(result.getCode()).isEqualTo(40001);
        assertThat(result.getMessage()).isEqualTo("invalid request parameter");
        assertThat(result.getTraceId()).isNotBlank();
    }

    /**
     * Maps an uncaught exception to {@code 50001} without leaking internals.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void mapsUnexpectedExceptionToInternalError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        Result<Void> result = handler.handleUnexpected(
                new IllegalStateException("database connection refused"), request);

        assertThat(result.getCode()).isEqualTo(50001);
        assertThat(result.getMessage()).isEqualTo("internal server error");
        assertThat(result.getMessage()).doesNotContain("database");
        assertThat(result.getTraceId()).isNotBlank();
    }

    /**
     * Reuses the inbound request id as the trace id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void reusesRequestIdHeaderAsTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("X-Request-Id", "req-abc-123");
        Result<Void> result = handler.handleBiz(
                new BizException(ErrorCode.STATE_CONFLICT), request);

        assertThat(result.getCode()).isEqualTo(40901);
        assertThat(result.getTraceId()).isEqualTo("req-abc-123");
    }
}
