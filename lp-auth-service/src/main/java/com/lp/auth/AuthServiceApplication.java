package com.lp.auth;

import com.lp.common.identity.TrustedIdentityConfig;
import com.lp.common.internal.InternalTokenConfig;
import com.lp.common.web.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth domain service entry point on port 8101.
 *
 * <p>Owns registration, login, token issuing and refresh, and user profile APIs. This milestone
 * implements the login chain only. Runtime configuration comes from Nacos once the config centre
 * client is wired in, and from the local defaults in {@code application.yml} until then.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
@MapperScan("com.lp.auth.mapper")
@EnableScheduling
@Import({GlobalExceptionHandler.class, InternalTokenConfig.class, TrustedIdentityConfig.class})
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
