package com.lp.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity handling switches for the gateway.
 *
 * <p>{@code enforce-mode} follows {@code docs/engineering-plan.md} 6.4: {@code obs} only logs,
 * {@code soft} injects headers and lets downstream services fall back, {@code hard} rejects a
 * request without a valid access token. The default is {@code hard} so the login chain is closed
 * from the first milestone.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.identity")
public class IdentityProperties {

    /** Enforcement mode: obs, soft or hard. */
    public String enforceMode = "hard";

    /** Whether verified identity headers are injected. */
    public boolean injectEnabled = true;

    /**
     * Paths that never require identity.
     *
     * <p>Only the token issuing endpoints are public; {@code /api/v1/auth/me} needs a verified
     * token so the gateway can inject the user id it reads.
     */
    public List<String> whitelist =
            List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/actuator/**",
                    "/error");

    /** Token verification settings. */
    public Jwt jwt = new Jwt();

    /**
     * Returns the enforcement mode.
     *
     * @return obs, soft or hard
     * @author Caspian
     * @since 0.1.0
     */
    public String getEnforceMode() {
        return enforceMode;
    }

    /**
     * Sets the enforcement mode.
     *
     * @param enforceMode obs, soft or hard
     * @author Caspian
     * @since 0.1.0
     */
    public void setEnforceMode(String enforceMode) {
        this.enforceMode = enforceMode;
    }

    /**
     * Tells whether verified headers are injected.
     *
     * @return {@code true} when injection is enabled
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isInjectEnabled() {
        return injectEnabled;
    }

    /**
     * Sets whether verified headers are injected.
     *
     * @param injectEnabled injection switch
     * @author Caspian
     * @since 0.1.0
     */
    public void setInjectEnabled(boolean injectEnabled) {
        this.injectEnabled = injectEnabled;
    }

    /**
     * Returns the public path patterns.
     *
     * @return immutable whitelist patterns
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getWhitelist() {
        return whitelist;
    }

    /**
     * Sets the public path patterns.
     *
     * @param whitelist Ant style patterns
     * @author Caspian
     * @since 0.1.0
     */
    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
    }

    /**
     * Returns the token verification settings.
     *
     * @return nested jwt settings
     * @author Caspian
     * @since 0.1.0
     */
    public Jwt getJwt() {
        return jwt;
    }

    /**
     * Sets the token verification settings.
     *
     * @param jwt nested settings
     * @author Caspian
     * @since 0.1.0
     */
    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    /**
     * Token verification settings.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public static class Jwt {

        /** Expected {@code iss} claim value. */
        public String issuer = "auth-service";

        /** PEM path of the RS256 public key. */
        public String publicKeyPath = "";

        /**
         * Returns the expected issuer.
         *
         * @return issuer claim value
         * @author Caspian
         * @since 0.1.0
         */
        public String getIssuer() {
            return issuer;
        }

        /**
         * Sets the expected issuer.
         *
         * @param issuer issuer claim value
         * @author Caspian
         * @since 0.1.0
         */
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        /**
         * Returns the public key path.
         *
         * @return PEM filesystem path, blank when unset
         * @author Caspian
         * @since 0.1.0
         */
        public String getPublicKeyPath() {
            return publicKeyPath;
        }

        /**
         * Sets the public key path.
         *
         * @param publicKeyPath PEM filesystem path
         * @author Caspian
         * @since 0.1.0
         */
        public void setPublicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
        }
    }
}
