package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.dto.PurchaseTicketView;
import com.lp.trade.dto.RedeemTicketView;
import com.lp.trade.purchase.AuthSessionClient;
import com.lp.trade.purchase.PurchaseTicketPayload;
import com.lp.trade.purchase.PurchaseTicketStore;
import com.lp.trade.service.PurchaseTicketService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Default purchase ticket flow.
 *
 * <p>Security choices:
 *
 * <ul>
 *   <li>The ticket is 256 bits from {@link java.security.SecureRandom}, not a signed payload. A
 *       signature would still need server state to be one-time; random plus Redis keeps the URL
 *       short and the state authoritative.
 *   <li>Neither the URL nor logs contain the user id. Only the ticket is in the query string.
 *   <li>Redemption is atomic in Redis, so two browsers cannot both win.
 *   <li>When the browser already has a session for another account, the ticket is rejected before
 *       it is consumed, so a mis-click cannot burn the real owner's link.
 * </ul>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class PurchaseTicketServiceImpl implements PurchaseTicketService {

    private static final int COURSE_PUBLISHED = 2;

    private final PurchaseTicketStore ticketStore;

    private final CourseInternalClient courseInternalClient;

    private final AuthSessionClient authSessionClient;

    private final TradeProperties tradeProperties;

    /**
     * Creates the service.
     *
     * @param ticketStore one-time ticket storage
     * @param courseInternalClient course lookup
     * @param authSessionClient token issuance
     * @param tradeProperties browser base URL
     * @author Caspian
     * @since 0.1.0
     */
    public PurchaseTicketServiceImpl(
            PurchaseTicketStore ticketStore,
            CourseInternalClient courseInternalClient,
            AuthSessionClient authSessionClient,
            TradeProperties tradeProperties) {
        this.ticketStore = ticketStore;
        this.courseInternalClient = courseInternalClient;
        this.authSessionClient = authSessionClient;
        this.tradeProperties = tradeProperties;
    }

    /**
     * Issues a ticket after checking the course is on sale.
     *
     * @param userId authenticated user id
     * @param courseId course to purchase
     * @return ticket, URL and expiry
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PurchaseTicketView issue(long userId, long courseId) {
        CourseSnapshot course = courseInternalClient.course(courseId);
        if (course.getStatus() != COURSE_PUBLISHED) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE);
        }
        String ticket = ticketStore.issue(userId, courseId);
        String url = baseUrl() + "/purchase?ticket=" + ticket;
        String expiresAt = Instant.now().plusSeconds(15 * 60).toString();
        return new PurchaseTicketView(url, url, ticket, expiresAt);
    }

    /**
     * Redeems a ticket into a browser session.
     *
     * @param ticket opaque ticket
     * @param callerUserId existing session user id, may be {@code null}
     * @return token pair and bound course id
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public RedeemTicketView redeem(String ticket, Long callerUserId) {
        if (ticket == null || ticket.isBlank()) {
            throw new BizException(ErrorCode.PURCHASE_TICKET_NOT_FOUND);
        }
        Optional<PurchaseTicketPayload> preview = ticketStore.peek(ticket);
        if (preview.isEmpty()) {
            throw absenceToException(ticketStore.classify(ticket));
        }
        PurchaseTicketPayload payload = preview.get();
        if (callerUserId != null && callerUserId != payload.getUserId()) {
            throw new BizException(ErrorCode.PURCHASE_TICKET_USER_MISMATCH);
        }
        Optional<PurchaseTicketPayload> taken = ticketStore.take(ticket);
        if (taken.isEmpty()) {
            throw absenceToException(ticketStore.classify(ticket));
        }
        ticketStore.markUsed(ticket);
        AuthSessionClient.SessionTokens tokens = authSessionClient.issue(payload.getUserId());
        return new RedeemTicketView(
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getExpiresIn(),
                payload.getCourseId());
    }

    /**
     * Maps a missing ticket to the documented error code.
     *
     * @param absence classification
     * @return exception to throw
     * @author Caspian
     * @since 0.1.0
     */
    private BizException absenceToException(PurchaseTicketStore.Absence absence) {
        return switch (absence) {
            case USED -> new BizException(ErrorCode.PURCHASE_TICKET_USED);
            case EXPIRED -> new BizException(ErrorCode.PURCHASE_TICKET_EXPIRED);
            case UNKNOWN -> new BizException(ErrorCode.PURCHASE_TICKET_NOT_FOUND);
        };
    }

    /**
     * Returns the configured browser base URL without a trailing slash.
     *
     * @return base URL
     * @author Caspian
     * @since 0.1.0
     */
    private String baseUrl() {
        String value = tradeProperties.getWebBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
