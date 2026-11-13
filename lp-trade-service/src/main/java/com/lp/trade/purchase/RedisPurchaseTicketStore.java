package com.lp.trade.purchase;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis backed purchase ticket store.
 *
 * <p>Key layout (all keys are derived from {@code sha256(ticket)}, never from the raw value):
 *
 * <ul>
 *   <li>{@code lp:trade:purchase-ticket:{hash}} - payload JSON, TTL 15 minutes.
 *   <li>{@code lp:trade:purchase-ticket:issued:{hash}} - marker, TTL 24 hours, distinguishes an
 *       expired ticket from one that never existed.
 *   <li>{@code lp:trade:purchase-ticket:used:{hash}} - marker, TTL 24 hours, distinguishes a
 *       redeemed ticket from an expired one.
 * </ul>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class RedisPurchaseTicketStore implements PurchaseTicketStore {

    /** Ticket validity. */
    public static final Duration TICKET_TTL = Duration.ofMinutes(15);

    /** How long the classification markers are kept. */
    public static final Duration MARKER_TTL = Duration.ofHours(24);

    private static final String TICKET_KEY = "lp:trade:purchase-ticket:";

    private static final String ISSUED_KEY = "lp:trade:purchase-ticket:issued:";

    private static final String USED_KEY = "lp:trade:purchase-ticket:used:";

    private static final int TICKET_BYTES = 32;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates the store.
     *
     * @param redisTemplate string Redis client
     * @param objectMapper JSON codec for the payload
     * @author Caspian
     * @since 0.1.0
     */
    public RedisPurchaseTicketStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Issues a random ticket and records its payload plus the issued marker.
     *
     * @param userId owner user id
     * @param courseId course to purchase
     * @return raw ticket value
     * @throws BizException {@code 50001} when Redis is unreachable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public String issue(long userId, long courseId) {
        byte[] bytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(ticket);
        PurchaseTicketPayload payload =
                new PurchaseTicketPayload(
                        userId, courseId, System.currentTimeMillis() + TICKET_TTL.toMillis());
        try {
            redisTemplate
                    .opsForValue()
                    .set(TICKET_KEY + hash, objectMapper.writeValueAsString(payload), TICKET_TTL);
            redisTemplate.opsForValue().set(ISSUED_KEY + hash, "1", MARKER_TTL);
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "purchase ticket store unavailable");
        }
        return ticket;
    }

    /**
     * Reads a ticket without deleting it.
     *
     * @param ticket raw ticket value
     * @return payload when present and parseable
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Optional<PurchaseTicketPayload> peek(String ticket) {
        try {
            String json = redisTemplate.opsForValue().get(TICKET_KEY + hash(ticket));
            return parse(json);
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "purchase ticket store unavailable");
        }
    }

    /**
     * Atomically reads and deletes the ticket.
     *
     * @param ticket raw ticket value
     * @return payload when this call consumed it
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Optional<PurchaseTicketPayload> take(String ticket) {
        try {
            String json = redisTemplate.opsForValue().getAndDelete(TICKET_KEY + hash(ticket));
            return parse(json);
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "purchase ticket store unavailable");
        }
    }

    /**
     * Writes the used marker after a successful redemption.
     *
     * @param ticket raw ticket value
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void markUsed(String ticket) {
        try {
            redisTemplate.opsForValue().set(USED_KEY + hash(ticket), "1", MARKER_TTL);
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "purchase ticket store unavailable");
        }
    }

    /**
     * Classifies a missing ticket using the long lived markers.
     *
     * @param ticket raw ticket value
     * @return used when the used marker exists, expired when only the issued marker exists
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Absence classify(String ticket) {
        String hash = hash(ticket);
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(USED_KEY + hash))) {
                return Absence.USED;
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(ISSUED_KEY + hash))) {
                return Absence.EXPIRED;
            }
            return Absence.UNKNOWN;
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "purchase ticket store unavailable");
        }
    }

    /**
     * Parses a stored payload, treating malformed or blank values as absent.
     *
     * @param json stored JSON
     * @return payload when valid
     * @author Caspian
     * @since 0.1.0
     */
    private Optional<PurchaseTicketPayload> parse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            PurchaseTicketPayload payload =
                    objectMapper.readValue(json, PurchaseTicketPayload.class);
            if (payload.getExpiresAt() <= System.currentTimeMillis()) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns the lowercase hex SHA-256 of a ticket.
     *
     * @param ticket raw ticket value
     * @return hex digest
     * @author Caspian
     * @since 0.1.0
     */
    private static String hash(String ticket) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(ticket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
