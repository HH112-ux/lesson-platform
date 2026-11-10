package com.lp.auth.config;

import com.lp.auth.security.JwtTokenService;
import com.lp.common.security.PemKeys;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires password hashing and token issuance beans.
 *
 * <p>Startup fails when the private key path is blank or unreadable, so a service that cannot sign
 * tokens never accepts traffic.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    MiniappProperties.class,
    AccountDeletionProperties.class
})
public class AuthConfiguration {

    /**
     * Creates the BCrypt password encoder used for login verification.
     *
     * @return stateless BCrypt encoder
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Creates the clock used to fix cancellation deadlines.
     *
     * <p>Injecting the clock keeps {@code effective_at} and the remaining seconds of a request
     * verifiable in tests without changing the production default.
     *
     * @return system clock in the JVM default zone, matching the database session zone
     * @author Caspian
     * @since 0.2.0
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Creates the token issuer from the configured PEM private key.
     *
     * @param properties token configuration
     * @return RS256 token issuer
     * @throws IllegalStateException when the private key path is blank or not an RSA key
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties) {
        if (properties.getPrivateKeyPath() == null || properties.getPrivateKeyPath().isBlank()) {
            throw new IllegalStateException(
                    "lp.auth.jwt.private-key-path is required; run scripts/gen-jwt-keys.sh");
        }
        PrivateKey key = PemKeys.loadPrivateKey(Path.of(properties.getPrivateKeyPath()));
        if (!(key instanceof RSAPrivateKey rsaPrivateKey)) {
            throw new IllegalStateException("lp.auth.jwt.private-key-path must be an RSA key");
        }
        return new JwtTokenService(
                rsaPrivateKey,
                properties.getIssuer(),
                Duration.ofSeconds(properties.getAccessTokenTtlSeconds()),
                Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));
    }
}
