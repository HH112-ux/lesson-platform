package com.lp.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origin policy for the gateway.
 *
 * <p>Origins are an explicit allow list; credentials are always allowed, so a wildcard origin is
 * rejected at startup instead of silently weakening the policy. An empty list is only accepted
 * under the {@code dev} profile, where the localhost defaults apply; every other profile fails
 * startup rather than falling back to localhost.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.cors")
public class CorsProperties {

    /** Whether the CORS filter is registered. */
    public boolean enabled = true;

    /** Exact origins allowed to call the gateway from a browser. */
    public List<String> allowedOrigins = List.of();

    /** How long a browser may cache a preflight result, in seconds. */
    public long maxAgeSeconds = 3600;

    /**
     * Returns whether the CORS filter is registered.
     *
     * @return {@code true} when enabled
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the CORS filter is registered.
     *
     * @param enabled filter switch
     * @author Caspian
     * @since 0.1.0
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the allowed origins.
     *
     * @return exact origins, empty when unset
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the allowed origins.
     *
     * @param allowedOrigins exact origins
     * @author Caspian
     * @since 0.1.0
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /**
     * Returns the preflight cache duration.
     *
     * @return duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    /**
     * Sets the preflight cache duration.
     *
     * @param maxAgeSeconds duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
