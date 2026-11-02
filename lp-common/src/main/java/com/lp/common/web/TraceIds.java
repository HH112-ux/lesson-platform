package com.lp.common.web;

import com.lp.common.identity.IdentityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the trace id attached to every response envelope.
 *
 * <p>Resolution order: the MDC value populated by the logging layer, then the
 * {@code X-Request-Id} request header, then a generated UUID. The result is never blank so
 * clients can always quote it in support requests.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class TraceIds {

    /** MDC key used by the logging configuration. */
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    /**
     * Resolves a non-blank trace id for the given request.
     *
     * @param request current request, may be {@code null}
     * @return trace id from MDC, from the request header, or a generated UUID
     * @author Caspian
     * @since 0.1.0
     */
    public static String resolve(HttpServletRequest request) {
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        if (request != null) {
            String fromHeader = request.getHeader(IdentityHeaders.REQUEST_ID);
            if (fromHeader != null && !fromHeader.isBlank()) {
                return fromHeader;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
