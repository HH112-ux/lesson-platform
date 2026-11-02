package com.lp.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI domain service entry point on port 8107.
 *
 * <p>Owns AI orchestration, data masking, local rule fallback, evaluation and metering. It holds
 * no business master data and ships no provider address or credential: model access arrives as
 * an encrypted Nacos configuration snapshot. While no provider is configured the skeleton answers
 * {@code /api/v1/ai/**} with error code {@code 50303}. Runtime configuration comes from the local
 * defaults in {@code application.yml} until the Nacos client is wired in.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class AiServiceApplication {

    /**
     * Starts the AI service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
