package com.lp.ugc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * UGC domain service entry point on port 8105.
 *
 * <p>Owns comments, favorites and reports. Content moderation advice comes from ai-service, but
 * the disposition decision stays with operations and is not made here. The skeleton exposes
 * health only; runtime configuration comes from Nacos once the config centre client is wired
 * in, and from the local defaults in {@code application.yml} until then.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class UgcServiceApplication {

    /**
     * Starts the UGC service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(UgcServiceApplication.class, args);
    }
}
