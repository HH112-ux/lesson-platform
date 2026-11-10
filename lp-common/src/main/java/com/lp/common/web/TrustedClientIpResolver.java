package com.lp.common.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resolves the real client address from a forwarded request without trusting client input.
 *
 * <p>{@code X-Forwarded-For} is append-only: each proxy appends the peer it observed, so the
 * right-most entry is the hop nearest to this service and every entry further left can be forged by
 * the caller. The resolver therefore only reads the header when the direct peer ({@code
 * remoteAddr}) is a configured trusted proxy, then walks the chain from right to left, skips
 * entries that are themselves trusted proxies, and returns the first untrusted address. When the
 * direct peer is not a trusted proxy the header is ignored entirely, so a client cannot spoof its
 * address by sending the header itself.
 *
 * <p>Only literal IPv4 and IPv6 addresses are accepted; host names are rejected instead of being
 * resolved, which would turn the resolution into a DNS lookup. Malformed entries are skipped. When
 * no usable entry remains the direct peer is returned, and when even that is unusable the constant
 * {@link #UNKNOWN} is returned so callers always get a stable, non-null key.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class TrustedClientIpResolver {

    /** Returned when neither the direct peer nor a forwarded entry is a usable address. */
    public static final String UNKNOWN = "unknown";

    /** Direct peers allowed to supply {@code X-Forwarded-For}, as CIDR blocks. */
    private final List<Cidr> trustedProxies;

    /**
     * Creates a resolver.
     *
     * @param trustedProxyCidrs trusted proxy ranges such as {@code 10.0.0.0/8} or {@code
     *     127.0.0.1}; blank elements are ignored, {@code null} means no proxy is trusted
     * @throws IllegalArgumentException when an element is not a valid IP or CIDR block
     * @author Caspian
     * @since 0.1.0
     */
    public TrustedClientIpResolver(Collection<String> trustedProxyCidrs) {
        List<Cidr> parsed = new ArrayList<>();
        if (trustedProxyCidrs != null) {
            for (String cidr : trustedProxyCidrs) {
                if (cidr != null && !cidr.isBlank()) {
                    parsed.add(Cidr.parse(cidr.trim()));
                }
            }
        }
        this.trustedProxies = List.copyOf(parsed);
    }

    /**
     * Resolves the client address from the direct peer and the forwarded header values.
     *
     * @param remoteAddr address of the directly connected peer, as reported by the container
     * @param forwardedForHeaders raw {@code X-Forwarded-For} values, each possibly comma separated;
     *     {@code null} or empty when the header is absent
     * @return a trustworthy client address, or {@link #UNKNOWN} when no address can be parsed
     * @author Caspian
     * @since 0.1.0
     */
    public String resolve(String remoteAddr, List<String> forwardedForHeaders) {
        String peer = normalize(remoteAddr);
        if (peer == null) {
            return UNKNOWN;
        }
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        List<String> chain = flatten(forwardedForHeaders);
        for (int index = chain.size() - 1; index >= 0; index--) {
            String candidate = normalize(chain.get(index));
            if (candidate == null) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return peer;
    }

    /**
     * Tells whether an address belongs to a configured trusted proxy range.
     *
     * @param ip literal IPv4 or IPv6 address
     * @return {@code true} when the address is inside one of the configured ranges
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isTrustedProxy(String ip) {
        byte[] address = parseAddress(ip);
        if (address == null) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> flatten(List<String> headers) {
        List<String> entries = new ArrayList<>();
        if (headers == null) {
            return entries;
        }
        for (String header : headers) {
            if (header == null) {
                continue;
            }
            for (String part : header.split(",")) {
                if (!part.isBlank()) {
                    entries.add(part.trim());
                }
            }
        }
        return entries;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String address = value.trim();
        if (address.isEmpty()) {
            return null;
        }
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0) {
                return null;
            }
            address = address.substring(1, end);
        } else if (address.indexOf(':') >= 0 && address.indexOf('.') >= 0) {
            int colon = address.lastIndexOf(':');
            String port = address.substring(colon + 1);
            if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) {
                address = address.substring(0, colon);
            }
        }
        return parseAddress(address) == null ? null : address;
    }

    private static byte[] parseAddress(String value) {
        if (value == null) {
            return null;
        }
        String address = value.trim();
        if (address.isEmpty()) {
            return null;
        }
        byte[] ipv4 = parseIpv4(address);
        if (ipv4 != null) {
            return ipv4;
        }
        if (address.indexOf(':') < 0) {
            return null;
        }
        for (int index = 0; index < address.length(); index++) {
            char current = address.charAt(index);
            boolean allowed =
                    (current >= '0' && current <= '9')
                            || (current >= 'a' && current <= 'f')
                            || (current >= 'A' && current <= 'F')
                            || current == ':'
                            || current == '.';
            if (!allowed) {
                return null;
            }
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int octet = 0;
            for (int offset = 0; offset < part.length(); offset++) {
                char current = part.charAt(offset);
                if (current < '0' || current > '9') {
                    return null;
                }
                octet = octet * 10 + (current - '0');
            }
            if (octet > 255) {
                return null;
            }
            bytes[index] = (byte) octet;
        }
        return bytes;
    }

    private static final class Cidr {

        private final byte[] network;

        private final int prefixLength;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        private static Cidr parse(String text) {
            String addressText = text;
            Integer prefix = null;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                addressText = text.substring(0, slash).trim();
                String prefixText = text.substring(slash + 1).trim();
                try {
                    prefix = Integer.parseInt(prefixText);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("invalid CIDR prefix: " + text, ex);
                }
            }
            byte[] address = parseAddress(addressText);
            if (address == null) {
                throw new IllegalArgumentException("invalid CIDR address: " + text);
            }
            int maxPrefix = address.length * 8;
            int prefixLength = prefix == null ? maxPrefix : prefix;
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                throw new IllegalArgumentException("CIDR prefix out of range: " + text);
            }
            byte[] network = address.clone();
            for (int bit = prefixLength; bit < maxPrefix; bit++) {
                network[bit / 8] &= (byte) ~(1 << (7 - (bit % 8)));
            }
            return new Cidr(network, prefixLength);
        }

        private boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != network[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
