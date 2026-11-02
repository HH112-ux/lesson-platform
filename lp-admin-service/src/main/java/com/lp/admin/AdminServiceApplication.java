package com.lp.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Admin domain service entry point on port 8106.
 *
 * <p>Owns back-office aggregation APIs, RBAC, the review queue, dashboards and teacher
 * qualification management. It never changes trade state directly; such actions are delegated
 * to trade-service. The skeleton exposes health only; runtime configuration comes from Nacos
 * once the config centre client is wired in, and from the local defaults in
 * {@code application.yml} until then.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class AdminServiceApplication {

    /**
     * Starts the admin service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
