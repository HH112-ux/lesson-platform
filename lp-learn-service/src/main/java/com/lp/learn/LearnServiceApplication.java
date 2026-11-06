package com.lp.learn;

import com.lp.common.internal.InternalTokenConfig;
import com.lp.common.web.GlobalExceptionHandler;
import com.lp.learn.config.LearnProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Learn domain service entry point on port 8104.
 *
 * <p>Owns entitlements, short lived playback URL signing and learning progress. It never changes
 * order state; purchasable rights are granted from trade events. The skeleton exposes health only;
 * runtime configuration comes from Nacos once the config centre client is wired in, and from the
 * local defaults in {@code application.yml} until then.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
@MapperScan("com.lp.learn.mapper")
@EnableConfigurationProperties(LearnProperties.class)
@Import({GlobalExceptionHandler.class, InternalTokenConfig.class})
public class LearnServiceApplication {

    /**
     * Starts the learn service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(LearnServiceApplication.class, args);
    }
}
