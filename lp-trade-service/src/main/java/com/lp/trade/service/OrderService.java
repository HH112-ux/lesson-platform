package com.lp.trade.service;

import com.lp.trade.dto.CancelOrderResult;
import com.lp.trade.dto.CreateOrderRequest;
import com.lp.trade.dto.OrderDetailView;
import com.lp.trade.dto.OrderPageView;
import com.lp.trade.dto.OrderView;

/**
 * Creates course orders.
 *
 * <p>Every call is idempotent by the client supplied key, so a retried request returns the original
 * order instead of charging twice.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface OrderService {

    /**
     * Creates an order for one course.
     *
     * @param userId authenticated buyer id
     * @param source trusted request source used to fix the payment channel
     * @param request course, idempotency key and optional coupon
     * @return created or replayed order
     * @throws com.lp.common.exception.BizException when the course is missing or not on sale, the
     *     buyer already owns it, or the coupon cannot be consumed
     * @author Caspian
     * @since 0.1.0
     */
    OrderView create(long userId, String source, CreateOrderRequest request);

    /**
     * Lists one page of the buyer orders, newest first.
     *
     * @param userId authenticated buyer id
     * @param status optional status filter
     * @param pageNum one based page number
     * @param pageSize page size
     * @return order page
     * @author Caspian
     * @since 0.1.0
     */
    OrderPageView list(long userId, Integer status, int pageNum, int pageSize);

    /**
     * Loads one order of the buyer.
     *
     * @param userId authenticated buyer id
     * @param orderId order primary key
     * @return order detail
     * @throws com.lp.common.exception.BizException when the order is absent or owned by someone
     *     else
     * @author Caspian
     * @since 0.1.0
     */
    OrderDetailView detail(long userId, long orderId);

    /**
     * Cancels a pending order and closes its pending payment attempts.
     *
     * @param userId authenticated buyer id
     * @param orderId order primary key
     * @param reason optional buyer supplied reason
     * @return resulting status, always 2
     * @throws com.lp.common.exception.BizException when the order is absent, owned by someone else
     *     or no longer pending
     * @author Caspian
     * @since 0.1.0
     */
    CancelOrderResult cancel(long userId, long orderId, String reason);
}
