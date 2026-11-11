package com.lp.trade.payment.alipay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.trade.config.AlipayPayProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that the credential loader fails fast instead of booting an unsigned channel.
 *
 * <p>The fixture holds placeholder keys only; the point of the test is the path and field handling,
 * not the key material.
 *
 * @author Caspian
 * @since 0.4.0
 */
class AlipayCredentialLoaderTest {

    private static final String FIXTURE =
            """
            {
              "appIds": [
                {
                  "appId": "2021000000000001",
                  "appPrivateKey": "placeholder-private-key",
                  "alipayPublicKey": "placeholder-public-key"
                }
              ]
            }
            """;

    private AlipayCredentials load(Path file, String... profiles) {
        AlipayPayProperties properties = new AlipayPayProperties();
        properties.setCredentialsFile(file.toString());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new AlipayCredentialLoader(properties, environment, new ObjectMapper()).load();
    }

    @Test
    void readsVerifiedFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("alipay-credentials.json");
        Files.writeString(file, FIXTURE);
        AlipayCredentials credentials = load(file);
        assertEquals("2021000000000001", credentials.appId());
        assertEquals("placeholder-private-key", credentials.appPrivateKey());
        assertEquals("placeholder-public-key", credentials.alipayPublicKey());
    }

    @Test
    void rejectsRelativePath(@TempDir Path directory) {
        AlipayPayProperties properties = new AlipayPayProperties();
        properties.setCredentialsFile(".alipay-sandbox.json");
        AlipayCredentialLoader loader =
                new AlipayCredentialLoader(properties, new MockEnvironment(), new ObjectMapper());
        assertThrows(IllegalStateException.class, loader::load);
    }

    @Test
    void rejectsIncompleteFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("incomplete.json");
        Files.writeString(file, "{\"appIds\":[{\"appId\":\"2021000000000001\"}]}");
        assertThrows(IllegalStateException.class, () -> load(file));
    }

    @Test
    void rejectsSandboxOnReleaseProfile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("alipay-credentials.json");
        Files.writeString(file, FIXTURE);
        assertThrows(IllegalStateException.class, () -> load(file, "prod"));
    }
}
