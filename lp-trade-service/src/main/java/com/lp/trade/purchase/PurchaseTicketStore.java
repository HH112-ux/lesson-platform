package com.lp.trade.purchase;

import java.util.Optional;

/**
 * Server side storage for one-time purchase tickets.
 *
 * <p>Tickets are opaque 256-bit random strings. The store never needs the raw ticket after the call
 * returns: it hashes it to build the key, so a log line or a heap dump cannot replay a ticket.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PurchaseTicketStore {

    /**
     * Issues a ticket valid for fifteen minutes.
     *
     * @param userId owner user id
     * @param courseId course to purchase
     * @return raw ticket value, safe to place in a URL
     * @author Caspian
     * @since 0.1.0
     */
    String issue(long userId, long courseId);

    /**
     * Reads a ticket without consuming it, used for the cross-user check before the atomic take.
     *
     * @param ticket raw ticket value
     * @return payload when the ticket is still valid
     * @author Caspian
     * @since 0.1.0
     */
    Optional<PurchaseTicketPayload> peek(String ticket);

    /**
     * Atomically reads and deletes a ticket, so two browsers can never redeem the same one.
     *
     * @param ticket raw ticket value
     * @return payload when this call won the race
     * @author Caspian
     * @since 0.1.0
     */
    Optional<PurchaseTicketPayload> take(String ticket);

    /**
     * Marks a consumed ticket as used so a later attempt can be told apart from an unknown one.
     *
     * @param ticket raw ticket value
     * @author Caspian
     * @since 0.1.0
     */
    void markUsed(String ticket);

    /**
     * Classifies a ticket that could not be taken.
     *
     * @param ticket raw ticket value
     * @return used, expired or unknown
     * @author Caspian
     * @since 0.1.0
     */
    Absence classify(String ticket);

    /** Why a ticket could not be redeemed. */
    enum Absence {
        /** The ticket was issued and redeemed before. */
        USED,
        /** The ticket was issued but its TTL elapsed. */
        EXPIRED,
        /** No such ticket was ever issued, or its marker was cleaned up. */
        UNKNOWN
    }
}
