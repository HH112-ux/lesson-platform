package com.lp.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Verifies that the internal token registration fails fast when the secret is absent.
 *
 * @author Caspian
 * @since 0.1.0
 */
class InternalTokenConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                    .withUserConfiguration(InternalTokenConfig.class)
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    /**
     * Refuses to start when {@code lp.internal.token} is not configured.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void failsStartupWithoutToken() {
        runner.run(
                context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("lp.internal.token");
                });
    }

    /**
     * Registers the filter when the secret is present.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void registersFilterWithToken() {
        runner.withPropertyValues("lp.internal.token=local-internal-token")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                        });
    }
}
