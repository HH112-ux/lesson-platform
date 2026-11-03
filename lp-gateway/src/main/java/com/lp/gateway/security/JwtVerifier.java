package com.lp.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import java.security.PublicKey;

/**
 * Verifies an RS256 access token issued by {@code auth-service}.
 *
 * <p>The verifier only reads claims. Signature, issuer and expiry are checked by jjwt, and any
 * failure is propagated as a {@code JwtException} subclass so the filter can map expiry to {@code
 * 40102} and everything else to {@code 40101}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class JwtVerifier {

    private final PublicKey publicKey;

    private final String issuer;

    /**
     * Creates a verifier.
     *
     * @param publicKey RSA public key matching the auth service private key
     * @param issuer expected {@code iss} claim
     * @author Caspian
     * @since 0.1.0
     */
    public JwtVerifier(PublicKey publicKey, String issuer) {
        this.publicKey = publicKey;
        this.issuer = issuer;
    }

    /**
     * Verifies the token and extracts the trusted identity.
     *
     * @param token JWT compact serialization without the bearer prefix
     * @return verified user id, role codes and permission codes
     * @throws io.jsonwebtoken.ExpiredJwtException when the token is expired
     * @throws io.jsonwebtoken.JwtException when the signature, issuer or payload is invalid
     * @author Caspian
     * @since 0.1.0
     */
    public VerifiedUser verify(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(publicKey)
                        .requireIssuer(issuer)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new MalformedJwtException("subject is not a user id", ex);
        }
        return new VerifiedUser(
                userId,
                VerifiedUser.rolesOf(claims.get("roles")),
                VerifiedUser.permissionsOf(claims.get("permissions")));
    }
}
