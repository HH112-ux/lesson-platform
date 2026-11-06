package com.lp.trade.service;

import com.lp.trade.dto.PurchaseTicketView;
import com.lp.trade.dto.RedeemTicketView;

/**
 * Issues and redeems one-time purchase tickets that move a miniapp buyer to the web checkout.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PurchaseTicketService {

    /**
     * Issues a ticket bound to the user and course.
     *
     * @param userId authenticated user id
     * @param courseId course to purchase
     * @return ticket, URL and expiry
     * @throws com.lp.common.exception.BizException {@code 40401} when the course is missing, {@code
     *     61001} when it is not on sale
     * @author Caspian
     * @since 0.1.0
     */
    PurchaseTicketView issue(long userId, long courseId);

    /**
     * Redeems a ticket and returns a browser session for its owner.
     *
     * @param ticket opaque ticket
     * @param callerUserId user id when the browser already has a session, otherwise {@code null}
     * @return token pair and the bound course id
     * @throws com.lp.common.exception.BizException {@code 62012} expired, {@code 62013} used,
     *     {@code 62014} unknown, {@code 62015} another user's ticket
     * @author Caspian
     * @since 0.1.0
     */
    RedeemTicketView redeem(String ticket, Long callerUserId);
}
