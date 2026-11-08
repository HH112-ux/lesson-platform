package com.lp.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * Verifies the trusted identity filter registration and whitelist assembly.
 *
 * @author Caspian
 * @since 0.2.0
 */
class TrustedIdentityConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                    .withUserConfiguration(TrustedIdentityConfig.class)
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    /**
     * Registers one servlet filter after the internal token filter, even without extra config.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void registersFilterWithDefaultWhitelist() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(TrustedIdentityFilter.class);
                    assertThat(registration.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
                });
    }

    /**
     * Merges configured public paths with the defaults and the internal endpoints.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void mergesConfiguredWhitelistWithoutDuplicates() {
        assertThat(
                        TrustedIdentityConfig.resolveWhitelist(
                                "/api/v1/courses/**, /internal/**, , /error"))
                .contains("/api/v1/auth/**", "/internal/**", "/api/v1/courses/**", "/error")
                .doesNotHaveDuplicates();
        assertThat(TrustedIdentityConfig.resolveWhitelist(null))
                .contains("/api/v1/auth/**", "/internal/**", "/error");
    }
}
