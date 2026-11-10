package com.lp.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the trust chain rules of {@link TrustedClientIpResolver}.
 *
 * @author Caspian
 * @since 0.1.0
 */
class TrustedClientIpResolverTest {

    private final TrustedClientIpResolver resolver =
            new TrustedClientIpResolver(List.of("10.0.0.0/8", "127.0.0.1", "2001:db8::/32"));

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        String client = resolver.resolve("203.0.113.7", List.of("198.51.100.9, 203.0.113.7"));

        assertThat(client).isEqualTo("203.0.113.7");
    }

    @Test
    void returnsNearestUntrustedHopFromTrustedPeer() {
        String client = resolver.resolve("127.0.0.1", List.of("198.51.100.9, 10.1.2.3, 127.0.0.1"));

        assertThat(client).isEqualTo("198.51.100.9");
    }

    @Test
    void ignoresForgedLeftMostEntry() {
        String client = resolver.resolve("10.0.0.5", List.of("1.1.1.1, 198.51.100.9"));

        assertThat(client).isEqualTo("198.51.100.9");
    }

    @Test
    void fallsBackToPeerWhenEveryHopIsTrusted() {
        String client = resolver.resolve("10.0.0.5", List.of("10.0.0.6, 127.0.0.1"));

        assertThat(client).isEqualTo("10.0.0.5");
    }

    @Test
    void skipsMalformedEntries() {
        String client =
                resolver.resolve("10.0.0.5", List.of("not-an-ip, evil.example.com, 198.51.100.9"));

        assertThat(client).isEqualTo("198.51.100.9");
    }

    @Test
    void returnsUnknownWhenNothingCanBeParsed() {
        assertThat(resolver.resolve(null, List.of("not-an-ip")))
                .isEqualTo(TrustedClientIpResolver.UNKNOWN);
        assertThat(resolver.resolve("10.0.0.5", List.of("not-an-ip"))).isEqualTo("10.0.0.5");
    }

    @Test
    void handlesIpv6AndPorts() {
        assertThat(resolver.resolve("2001:db8::1", List.of("[2001:db9::2]:443")))
                .isEqualTo("2001:db9::2");
        assertThat(resolver.resolve("198.51.100.9:52100", List.of())).isEqualTo("198.51.100.9");
        assertThat(resolver.resolve("2001:db8::1", List.of("2001:db8::2")))
                .isEqualTo("2001:db8::1");
    }

    @Test
    void flattensMultipleHeaderValues() {
        String client =
                resolver.resolve("10.0.0.5", List.of("198.51.100.8", "10.2.3.4, 127.0.0.1"));

        assertThat(client).isEqualTo("198.51.100.8");
    }

    @Test
    void rejectsInvalidCidr() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrustedClientIpResolver(List.of("10.0.0.0/33")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrustedClientIpResolver(List.of("not-an-ip")));
    }
}
