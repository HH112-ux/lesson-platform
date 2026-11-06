package com.lp.trade.service;

import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.RefundView;

/**
 * Refunds a paid order through its original payment channel.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface RefundService {

    /**
     * Refunds one paid order of the current buyer.
     *
     * <p>Repeating the call returns the original successful refund instead of refunding again. The
     * first release supports full refunds only, so a partial or excessive amount is rejected.
     *
     * @param userId buyer id from the gateway header
     * @param orderId order primary key
     * @param request optional amount and reason
     * @return successful refund
     * @throws com.lp.common.exception.BizException when the order is absent, not owned, not paid,
     *     or the amount is not the paid amount
     * @author Caspian
     * @since 0.1.0
     */
    RefundView refund(long userId, long orderId, ApplyRefundRequest request);
}
