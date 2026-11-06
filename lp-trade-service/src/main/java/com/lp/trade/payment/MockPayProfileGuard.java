package com.lp.trade.payment;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the local mock payment provider is enabled outside development.
 *
 * <p>The mock provider signs callbacks with a shared local secret, which is unacceptable on a
 * deployment that could reach real buyers. Enabling {@code lp.pay.mock.enabled} with the {@code
 * prod} or {@code staging} profile aborts startup instead of degrading silently.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Component
@ConditionalOnProperty(prefix = "lp.pay.mock", name = "enabled", havingValue = "true")
public class MockPayProfileGuard {

    private static final Logger LOG = LoggerFactory.getLogger(MockPayProfileGuard.class);

    private static final List<String> FORBIDDEN_PROFILES = List.of("prod", "staging");

    private final Environment environment;

    /**
     * Creates the guard with the active environment.
     *
     * @param environment application environment used to read active profiles
     * @author Caspian
     * @since 0.2.0
     */
    public MockPayProfileGuard(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fails startup when a forbidden profile is active.
     *
     * @throws IllegalStateException when the mock provider is enabled on prod or staging
     * @author Caspian
     * @since 0.2.0
     */
    @PostConstruct
    public void verifyProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (FORBIDDEN_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "lp.pay.mock.enabled=true is forbidden on profile '"
                                + profile
                                + "'; the local mock payment channel may only run on dev or"
                                + " local");
            }
        }
        LOG.warn(
                "local mock payment channel is enabled; callbacks are signed with"
                        + " lp.pay.mock.secret and must never reach real buyers");
    }
}
