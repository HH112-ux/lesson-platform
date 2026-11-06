package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.PurchaseTicketRequest;
import com.lp.trade.dto.PurchaseTicketView;
import com.lp.trade.dto.RedeemTicketRequest;
import com.lp.trade.dto.RedeemTicketView;
import com.lp.trade.service.PurchaseTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase ticket endpoints that move a miniapp buyer to the web checkout.
 *
 * <p>{@code POST /purchase-ticket} requires a signed-in miniapp user. {@code POST
 * /purchase-ticket/redeem} is optional-auth through the gateway: the browser opened by the QR code
 * may have no session yet.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/orders/purchase-ticket")
public class PurchaseTicketController {

    private final PurchaseTicketService purchaseTicketService;

    /**
     * Creates the controller.
     *
     * @param purchaseTicketService ticket flow
     * @author Caspian
     * @since 0.1.0
     */
    public PurchaseTicketController(PurchaseTicketService purchaseTicketService) {
        this.purchaseTicketService = purchaseTicketService;
    }

    /**
     * Issues a ticket for the signed-in user.
     *
     * @param body request carrying the course id
     * @param userId gateway injected user id
     * @param request current request, used to resolve the trace id
     * @return ticket, URL and expiry
     * @throws BizException {@code 40101} when called anonymously, {@code 40001} when the course id
     *     is invalid
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping
    public Result<PurchaseTicketView> issue(
            @RequestBody PurchaseTicketRequest body,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (body == null || body.getCourseId() == null || body.getCourseId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseId is required");
        }
        return Result.ok(
                purchaseTicketService.issue(userId, body.getCourseId()), TraceIds.resolve(request));
    }

    /**
     * Redeems a ticket into a browser session.
     *
     * @param body request carrying the ticket
     * @param userId gateway injected user id, present only when the browser already has a session
     * @param request current request, used to resolve the trace id
     * @return token pair and the bound course id
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/redeem")
    public Result<RedeemTicketView> redeem(
            @RequestBody RedeemTicketRequest body,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        if (body == null || body.getTicket() == null || body.getTicket().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ticket is required");
        }
        return Result.ok(
                purchaseTicketService.redeem(body.getTicket(), userId), TraceIds.resolve(request));
    }
}
