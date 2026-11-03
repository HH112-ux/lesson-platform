package com.lp.auth.security;

import io.jsonwebtoken.Jwts;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues RS256 access tokens and opaque refresh tokens.
 *
 * <p>Access token claims: {@code sub} carries the user id, {@code roles} carries role codes,
 * {@code iss} carries the configured issuer and the token lives for the configured lifetime.
 * The gateway verifies the same claims with the matching public key.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class JwtTokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RSAPrivateKey privateKey;

    private final String issuer;

    private final Duration accessTtl;

    private final Duration refreshTtl;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates the issuer.
     *
     * @param privateKey RSA private key used for RS256 signing
     * @param issuer     value of the {@code iss} claim
     * @param accessTtl  access token lifetime
     * @param refreshTtl refresh token lifetime
     * @author Caspian
     * @since 0.1.0
     */
    public JwtTokenService(RSAPrivateKey privateKey, String issuer, Duration accessTtl,
                           Duration refreshTtl) {
        this.privateKey = privateKey;
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    /**
     * Issues a token pair for an authenticated user.
     *
     * @param userId authentic user id
     * @param roles  role codes granted to the user, may be empty
     * @return signed access token and opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public AuthTokens issue(long userId, List<String> roles) {
        Instant now = Instant.now();
        String accessToken = Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .claim("roles", roles == null ? List.of() : List.copyOf(roles))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new AuthTokens(accessToken, randomRefreshToken(), accessTtl.toSeconds());
    }

    /**
     * Returns the configured refresh token lifetime in seconds.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getRefreshTtlSeconds() {
        return refreshTtl.toSeconds();
    }

    /**
     * Generates a URL safe random refresh token.
     *
     * @return base64url encoded random value
     * @author Caspian
     * @since 0.1.0
     */
    private String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
