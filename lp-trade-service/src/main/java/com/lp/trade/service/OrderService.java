package com.lp.trade.service;

import com.lp.trade.dto.CreateOrderRequest;
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
}
