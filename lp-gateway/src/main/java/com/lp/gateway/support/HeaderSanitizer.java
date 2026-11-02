package com.lp.gateway.support;

import com.lp.common.identity.IdentityHeaders;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;

/**
 * Removes inbound identity headers, including case variants and duplicates.
 *
 * <p>The class is a pure function so that header stripping can be unit tested without a
 * WebFlux runtime. Only the gateway may add trusted identity headers afterwards.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class HeaderSanitizer {

    private static final Set<String> BANNED = IdentityHeaders.SANITIZE_HEADERS.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    private HeaderSanitizer() {
    }

    /**
     * Returns a copy of the given headers without any banned identity header.
     *
     * <p>The input map is not modified. All values of a banned header are dropped at once,
     * and the comparison is case insensitive, so {@code x-user-id} is dropped just like
     * {@code X-User-Id}.</p>
     *
     * @param inbound original request headers
     * @return sanitized headers safe to forward
     * @author Caspian
     * @since 0.1.0
     */
    public static HttpHeaders sanitize(HttpHeaders inbound) {
        HttpHeaders sanitized = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : inbound.entrySet()) {
            if (isBanned(entry.getKey())) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }

    /**
     * Tells whether a header may never be supplied by a client.
     *
     * @param headerName header name, may be {@code null}
     * @return {@code true} when the header is in the strip list, ignored case
     * @author Caspian
     * @since 0.1.0
     */
    public static boolean isBanned(String headerName) {
        return headerName != null && BANNED.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
