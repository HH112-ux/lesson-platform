package com.lp.learn.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared beans of the learn service.
 *
 * <p>The clock is a bean so playback token expiry is deterministic in tests.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Configuration
public class LearnConfig {

    /**
     * Provides the system clock in UTC.
     *
     * @return UTC clock
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
