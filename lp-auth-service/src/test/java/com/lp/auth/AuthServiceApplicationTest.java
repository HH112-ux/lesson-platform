package com.lp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the auth service context loads without external infrastructure.
 *
 * <p>The H2 test profile replaces the MySQL datasource. A throwaway RSA key is generated
 * into a temporary file before the context starts, so the real token issuer bean is created
 * without committing key material to the repository.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AuthServiceApplicationTest {

    static {
        writeEphemeralKey();
    }

    /**
     * Boots the application context; absent MySQL, Redis and Nacos must not block startup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void contextLoads() {
    }

    /**
     * Generates a throwaway RSA private key and points the configuration at it.
     *
     * @author Caspian
     * @since 0.1.0
     */
    private static void writeEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Path keyFile = Files.createTempFile("lp-auth-test", ".pem");
            Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes())
                            .encodeToString(keyPair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n");
            keyFile.toFile().deleteOnExit();
            System.setProperty("lp.auth.jwt.private-key-path", keyFile.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot prepare test RSA key", ex);
        }
    }
}
