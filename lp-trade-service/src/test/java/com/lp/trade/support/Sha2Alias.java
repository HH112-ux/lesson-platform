package com.lp.trade.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Test-only stand-in for the MySQL {@code SHA2} function used by the anonymisation statement.
 *
 * <p>The trade tests run the production SQL on H2, which has no {@code SHA2}. Registering this
 * method as an alias keeps the statement itself under test instead of moving the digest into Java
 * just to make it portable. Living in test sources guarantees the shim is absent in production.
 *
 * @author Caspian
 * @since 0.3.0
 */
public final class Sha2Alias {

    private Sha2Alias() {}

    /**
     * Returns the lower case hex SHA-256 digest of a value.
     *
     * @param value text to digest
     * @param bits digest size, {@code 256} is the only supported value
     * @return 64 hex characters
     * @throws NoSuchAlgorithmException when the runtime provides no SHA-256
     * @author Caspian
     * @since 0.3.0
     */
    public static String sha2(String value, int bits) throws NoSuchAlgorithmException {
        if (bits != 256) {
            throw new IllegalArgumentException("only SHA-256 is supported");
        }
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte current : digest) {
            hex.append(String.format("%02x", current));
        }
        return hex.toString();
    }
}
