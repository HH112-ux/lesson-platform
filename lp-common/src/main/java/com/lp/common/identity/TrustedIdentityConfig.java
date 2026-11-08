package com.lp.common.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link TrustedIdentityFilter} in every servlet business service.
 *
 * <p>Business services must never trust identity headers on their own. Registering this filter once
 * makes a request without a gateway injected {@code X-User-Id} fail with {@code 40101} before it
 * reaches a controller, instead of being silently treated as anonymous or, worse, as whoever the
 * caller claims to be.
 *
 * <p>Internal service-to-service calls are whitelisted here because they are authenticated by
 * {@code InternalTokenFilter} instead, and public entry points (login, channel callbacks, course
 * catalogue, redeemed purchase tickets) are supplied per service through {@code
 * lp.identity.whitelist}, a comma separated list of Ant patterns.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Configuration(proxyBeanMethods = false)
public class TrustedIdentityConfig {

    /**
     * Registers the filter on every path, after the internal token filter.
     *
     * @param objectMapper serializer used to write the unauthenticated envelope
     * @param whitelist comma separated extra Ant patterns from {@code lp.identity.whitelist}
     * @return filter registration bound to {@code /*}
     * @author Caspian
     * @since 0.2.0
     */
    @Bean
    public FilterRegistrationBean<TrustedIdentityFilter> trustedIdentityFilter(
            ObjectMapper objectMapper, @Value("${lp.identity.whitelist:}") String whitelist) {
        FilterRegistrationBean<TrustedIdentityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(
                new TrustedIdentityFilter(objectMapper, resolveWhitelist(whitelist)));
        registration.addUrlPatterns("/*");
        // Runs last so InternalTokenFilter has already vetted /internal/** traffic.
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    /**
     * Merges the fixed whitelist with the service specific patterns.
     *
     * @param whitelist comma separated Ant patterns, possibly empty
     * @return whitelist including defaults, internal endpoints and configured public paths
     * @author Caspian
     * @since 0.2.0
     */
    static List<String> resolveWhitelist(String whitelist) {
        List<String> patterns = new ArrayList<>(TrustedIdentityFilter.DEFAULT_WHITELIST);
        patterns.add("/internal/**");
        if (whitelist != null) {
            for (String pattern : whitelist.split(",")) {
                String trimmed = pattern.trim();
                if (!trimmed.isEmpty() && !patterns.contains(trimmed)) {
                    patterns.add(trimmed);
                }
            }
        }
        return List.copyOf(patterns);
    }
}
