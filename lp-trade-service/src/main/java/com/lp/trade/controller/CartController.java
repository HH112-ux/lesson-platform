package com.lp.trade.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.trade.dto.AddCartItemRequest;
import com.lp.trade.dto.CartAddResult;
import com.lp.trade.dto.CartRemoveResult;
import com.lp.trade.dto.CartView;
import com.lp.trade.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart endpoints.
 *
 * <p>Identity comes exclusively from the gateway injected {@code X-User-Id}; the service never
 * accepts a user id from the body or query string.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param cartService cart operations
     * @author Caspian
     * @since 0.1.0
     */
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Adds a course to the cart; repeated calls are idempotent.
     *
     * @param userId gateway injected user id, may be absent when called without a token
     * @param body request body carrying the course id
     * @param request current request, used to resolve the trace id
     * @return {@code {added:true}}, or an error code
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/items")
    public Result<CartAddResult> add(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestBody(required = false) AddCartItemRequest body,
            HttpServletRequest request) {
        long uid = requireUser(userId);
        if (body == null || body.getCourseId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "courseId is required");
        }
        cartService.addItem(uid, body.getCourseId());
        return Result.ok(new CartAddResult(true), TraceIds.resolve(request));
    }

    /**
     * Returns the cart of the current user.
     *
     * @param userId gateway injected user id, may be absent when called without a token
     * @param request current request, used to resolve the trace id
     * @return cart lines with current course data
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<CartView> cart(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            HttpServletRequest request) {
        return Result.ok(cartService.cart(requireUser(userId)), TraceIds.resolve(request));
    }

    /**
     * Removes a course from the cart; removing an absent row is a success.
     *
     * @param userId gateway injected user id, may be absent when called without a token
     * @param courseId course id
     * @param request current request, used to resolve the trace id
     * @return {@code {removed:true}}
     * @author Caspian
     * @since 0.1.0
     */
    @DeleteMapping("/items/{courseId}")
    public Result<CartRemoveResult> remove(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @PathVariable long courseId,
            HttpServletRequest request) {
        cartService.removeItem(requireUser(userId), courseId);
        return Result.ok(new CartRemoveResult(true), TraceIds.resolve(request));
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
