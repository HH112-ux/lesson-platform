package com.lp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth domain service entry point on port 8101.
 *
 * <p>Owns registration, login, token issuing and refresh, and user profile APIs. The skeleton
 * exposes health only; business behaviour arrives in later milestones. Runtime configuration
 * comes from Nacos once the config centre client is wired in, and from the local defaults in
 * {@code application.yml} until then.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class AuthServiceApplication {

    /**
     * Starts the auth service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
