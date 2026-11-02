package com.lp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway entry point on port 8080.
 *
 * <p>Owns routing, identity header stripping and injection, rate limiting and CORS. It must
 * not contain business logic; every business call is forwarded to a domain service through
 * the route table in {@code routes.yaml}.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * Starts the gateway.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
