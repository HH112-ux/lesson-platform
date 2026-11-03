package com.lp.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads RSA private and public keys from PEM files.
 *
 * <p>Only the two formats produced by {@code openssl genpkey} / {@code openssl rsa -pubout}
 * are accepted: {@code BEGIN PRIVATE KEY} (PKCS#8) and {@code BEGIN PUBLIC KEY} (X.509).
 * Encrypted keys, certificates and other PEM labels are rejected so that a misconfigured
 * file fails at startup instead of silently weakening signature verification.</p>
 *
 * <p>Key material stays in memory only; the loader never logs or returns the encoded bytes.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class PemKeys {

    private static final String PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_FOOTER = "-----END PUBLIC KEY-----";

    private PemKeys() {
    }

    /**
     * Reads an unencrypted PKCS#8 RSA private key from a PEM file.
     *
     * @param path filesystem path of the PEM file
     * @return parsed RSA private key
     * @throws IllegalStateException when the file is missing, unreadable or not a PKCS#8 key
     * @author Caspian
     * @since 0.1.0
     */
    public static PrivateKey loadPrivateKey(Path path) {
        return parsePrivateKey(readPem(path));
    }

    /**
     * Reads an X.509 RSA public key from a PEM file.
     *
     * @param path filesystem path of the PEM file
     * @return parsed RSA public key
     * @throws IllegalStateException when the file is missing, unreadable or not an X.509 key
     * @author Caspian
     * @since 0.1.0
     */
    public static PublicKey loadPublicKey(Path path) {
        return parsePublicKey(readPem(path));
    }

    /**
     * Parses a PKCS#8 RSA private key from PEM text.
     *
     * @param pem complete PEM document
     * @return parsed RSA private key
     * @throws IllegalStateException when the text does not contain a valid PKCS#8 RSA key
     * @author Caspian
     * @since 0.1.0
     */
    public static PrivateKey parsePrivateKey(String pem) {
        byte[] der = decode(pem, PRIVATE_HEADER, PRIVATE_FOOTER);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("invalid RSA private key", ex);
        }
    }

    /**
     * Parses an X.509 RSA public key from PEM text.
     *
     * @param pem complete PEM document
     * @return parsed RSA public key
     * @throws IllegalStateException when the text does not contain a valid X.509 RSA key
     * @author Caspian
     * @since 0.1.0
     */
    public static PublicKey parsePublicKey(String pem) {
        byte[] der = decode(pem, PUBLIC_HEADER, PUBLIC_FOOTER);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("invalid RSA public key", ex);
        }
    }

    /**
     * Reads the file content, wrapping IO failures into an unchecked exception.
     *
     * @param path filesystem path
     * @return file content
     * @throws IllegalStateException when the file cannot be read
     * @author Caspian
     * @since 0.1.0
     */
    private static String readPem(Path path) {
        if (path == null) {
            throw new IllegalStateException("pem path is not configured");
        }
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read pem file " + path, ex);
        }
    }

    /**
     * Extracts the base64 body between the expected header and footer.
     *
     * @param pem    PEM document
     * @param header required begin line
     * @param footer required end line
     * @return decoded DER bytes
     * @throws IllegalStateException when the expected label is absent or body is undecodable
     * @author Caspian
     * @since 0.1.0
     */
    private static byte[] decode(String pem, String header, String footer) {
        int begin = pem.indexOf(header);
        int end = pem.indexOf(footer);
        if (begin < 0 || end < begin) {
            throw new IllegalStateException("pem does not contain " + header + " and " + footer);
        }
        String body = pem.substring(begin + header.length(), end).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("pem body is not valid base64", ex);
        }
    }
}
