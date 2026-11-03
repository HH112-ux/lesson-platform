package com.lp.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies RS256 signing and the claims the gateway later reads.
 *
 * @author Caspian
 * @since 0.1.0
 */
class JwtTokenServiceTest {

    private static KeyPair keyPair;

    /**
     * Generates a throwaway RSA key pair; no key file is needed for unit tests.
     *
     * @throws Exception when the RSA provider is unavailable
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * Issues a token and verifies its signature, subject, roles and lifetime.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issuesVerifiableRs256Token() {
        JwtTokenService service = new JwtTokenService((RSAPrivateKey) keyPair.getPrivate(),
                "auth-service", Duration.ofMinutes(15), Duration.ofDays(7));

        AuthTokens tokens = service.issue(42L, List.of("STUDENT", "TEACHER"));

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .requireIssuer("auth-service")
                .build()
                .parseSignedClaims(tokens.getAccessToken())
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("roles", List.class)).containsExactly("STUDENT", "TEACHER");
        assertThat(tokens.getExpiresInSeconds()).isEqualTo(900);
        assertThat(claims.getExpiration().toInstant())
                .isAfter(Instant.now().plus(Duration.ofMinutes(14)))
                .isBefore(Instant.now().plus(Duration.ofMinutes(16)));
        assertThat(tokens.getRefreshToken()).isNotBlank().isNotEqualTo(tokens.getAccessToken());
        assertThat(service.getRefreshTtlSeconds()).isEqualTo(604800);
    }

    /**
     * Issues distinct refresh tokens for repeated logins.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issuesUniqueRefreshTokens() {
        JwtTokenService service = new JwtTokenService((RSAPrivateKey) keyPair.getPrivate(),
                "auth-service", Duration.ofMinutes(15), Duration.ofDays(7));

        assertThat(service.issue(7L, List.of()).getRefreshToken())
                .isNotEqualTo(service.issue(7L, List.of()).getRefreshToken());
    }
}
