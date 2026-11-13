package com.lp.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Verifies that {@link HeaderSanitizer} drops every client supplied identity header.
 *
 * @author Caspian
 * @since 0.1.0
 */
class HeaderSanitizerTest {

    /**
     * Drops trusted headers written in any casing or with duplicate values.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void stripsTrustedHeadersIgnoringCase() {
        HttpHeaders inbound = new HttpHeaders();
        inbound.add("X-User-Id", "999");
        inbound.add("x-user-id", "1000");
        inbound.add("x-user-roles", "ADMIN");
        inbound.add("X-USER-SOURCE", "web");
        inbound.add("X-Tenant-Id", "tenant-a");
        inbound.add("Authorization", "Bearer token");
        inbound.add("X-Request-Id", "req-1");

        HttpHeaders sanitized = HeaderSanitizer.sanitize(inbound);

        assertThat(sanitized.get("X-User-Id")).isNull();
        assertThat(sanitized.get("X-User-Roles")).isNull();
        assertThat(sanitized.get("X-User-Source")).isNull();
        assertThat(sanitized.get("X-Tenant-Id")).isNull();
        assertThat(sanitized.getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(sanitized.getFirst("X-Request-Id")).isEqualTo("req-1");
    }

    /**
     * Drops personal data variants that clients must never provide.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void stripsBannedPersonalDataHeaders() {
        HttpHeaders inbound = new HttpHeaders();
        inbound.add("X-User-Phone", "13800000000");
        inbound.add("x-user-email", "user@example.com");
        inbound.add("Accept", "application/json");

        HttpHeaders sanitized = HeaderSanitizer.sanitize(inbound);

        assertThat(sanitized.containsHeader("X-User-Phone")).isFalse();
        assertThat(sanitized.containsHeader("X-User-Email")).isFalse();
        assertThat(sanitized.getFirst("Accept")).isEqualTo("application/json");
    }

    /**
     * Leaves the inbound header map untouched so callers can still log the original request.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void doesNotMutateInboundHeaders() {
        HttpHeaders inbound = new HttpHeaders();
        inbound.add("X-User-Id", "999");

        HeaderSanitizer.sanitize(inbound);

        assertThat(inbound.getFirst("X-User-Id")).isEqualTo("999");
    }

    /**
     * Classifies header names case insensitively.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void classifiesHeaderNamesCaseInsensitively() {
        assertThat(HeaderSanitizer.isBanned("X-User-Id")).isTrue();
        assertThat(HeaderSanitizer.isBanned("x-user-id")).isTrue();
        assertThat(HeaderSanitizer.isBanned("Authorization")).isFalse();
        assertThat(HeaderSanitizer.isBanned(null)).isFalse();
    }
}
