package com.lp.common.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link InternalTokenFilter} for services that expose {@code /internal/v1/**}.
 *
 * <p>Import this configuration only in services with internal endpoints. The {@code
 * lp.internal.token} property has no default, so an environment without {@code LP_INTERNAL_TOKEN}
 * fails startup instead of running with an open internal API.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class InternalTokenConfig {

    /**
     * Binds the filter to every {@code /internal/*} route.
     *
     * @param objectMapper serializer used by the filter for the error envelope
     * @param expectedToken shared secret from the environment, must not be blank
     * @return registration restricted to the internal API prefix
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            ObjectMapper objectMapper, @Value("${lp.internal.token}") String expectedToken) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalTokenFilter(expectedToken, objectMapper));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
