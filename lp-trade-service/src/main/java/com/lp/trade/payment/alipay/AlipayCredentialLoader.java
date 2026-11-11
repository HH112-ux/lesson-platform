package com.lp.trade.payment.alipay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.trade.config.AlipayPayProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Reads the Alipay credentials from the verified credential file at startup.
 *
 * <p>The file is read directly from its configured absolute path; values are never copied into
 * {@code application.yml}, environment variables, logs or error messages. Any missing field aborts
 * startup instead of degrading into an unsigned channel. Key material is parsed and validated when
 * the signing adapter is wired in the next slice.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class AlipayCredentialLoader {

    private static final Logger LOG = LoggerFactory.getLogger(AlipayCredentialLoader.class);

    private static final List<String> FORBIDDEN_PROFILES = List.of("prod", "staging");

    private final AlipayPayProperties properties;

    private final Environment environment;

    private final ObjectMapper objectMapper;

    /**
     * Creates the loader with its settings.
     *
     * @param properties channel settings, including the credential file path
     * @param environment application environment used to read the active profiles
     * @param objectMapper JSON reader of the credential file
     * @author Caspian
     * @since 0.4.0
     */
    public AlipayCredentialLoader(
            AlipayPayProperties properties, Environment environment, ObjectMapper objectMapper) {
        this.properties = properties;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and validates the credentials.
     *
     * @return validated credentials
     * @throws IllegalStateException when the path is missing, not absolute, unreadable, incomplete,
     *     or points at a sandbox application while a release profile is active
     * @author Caspian
     * @since 0.4.0
     */
    public AlipayCredentials load() {
        Path path = resolvePath();
        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "lp.pay.alipay.credentials-file is not readable: " + path, exception);
        }
        AlipayCredentials credentials = readCredentials(root);
        rejectSandboxOnReleaseProfile();
        LOG.info("alipay channel configured for appId={}", credentials.appId());
        return credentials;
    }

    private Path resolvePath() {
        String configured = properties.getCredentialsFile();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException(
                    "lp.pay.alipay.credentials-file must point at the verified credential file");
        }
        Path path = Path.of(configured.trim());
        if (!path.isAbsolute()) {
            // A relative path would resolve against the process working directory, so the
            // deployment could silently read a different file than the operator verified.
            throw new IllegalStateException(
                    "lp.pay.alipay.credentials-file must be an absolute path");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(
                    "lp.pay.alipay.credentials-file does not exist or is not readable: " + path);
        }
        return path;
    }

    private AlipayCredentials readCredentials(JsonNode root) {
        JsonNode applications = root.path("appIds");
        if (!applications.isArray() || applications.isEmpty()) {
            throw new IllegalStateException("credential file has no appIds entry");
        }
        JsonNode application = applications.get(0);
        String appId = text(application, "appId");
        String appPrivateKey = text(application, "appPrivateKey");
        String alipayPublicKey = text(application, "alipayPublicKey");
        return new AlipayCredentials(appId, appPrivateKey, alipayPublicKey);
    }

    private String text(JsonNode application, String field) {
        String value = application.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("credential file is missing field '" + field + "'");
        }
        return value.trim();
    }

    private void rejectSandboxOnReleaseProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (FORBIDDEN_PROFILES.contains(profile.toLowerCase(Locale.ROOT))
                    && properties.getGatewayUrl().contains("sandbox")) {
                throw new IllegalStateException(
                        "the Alipay sandbox gateway is forbidden on profile '" + profile + "'");
            }
        }
    }
}
