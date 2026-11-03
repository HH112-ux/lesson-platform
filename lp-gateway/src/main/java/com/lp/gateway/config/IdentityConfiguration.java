package com.lp.gateway.config;

import com.lp.common.security.PemKeys;
import com.lp.gateway.security.JwtVerifier;
import java.nio.file.Path;
import java.security.PublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the token verifier from the configured public key.
 *
 * <p>A gateway without a readable public key cannot verify any request, so the bean fails at
 * startup instead of silently accepting malformed tokens.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfiguration {

    /**
     * Creates the RS256 verifier.
     *
     * @param properties identity settings
     * @return verifier bound to the configured key and issuer
     * @throws IllegalStateException when the public key path is blank or unreadable
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    public JwtVerifier jwtVerifier(IdentityProperties properties) {
        String keyPath = properties.getJwt().getPublicKeyPath();
        if (keyPath == null || keyPath.isBlank()) {
            throw new IllegalStateException(
                    "lp.identity.jwt.public-key-path is required; run scripts/gen-jwt-keys.sh");
        }
        PublicKey publicKey = PemKeys.loadPublicKey(Path.of(keyPath));
        return new JwtVerifier(publicKey, properties.getJwt().getIssuer());
    }
}
