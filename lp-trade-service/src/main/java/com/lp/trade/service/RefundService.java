package com.lp.trade.service;

import com.lp.trade.dto.ApplyRefundRequest;
import com.lp.trade.dto.RefundAdminPageView;
import com.lp.trade.dto.RefundView;

/**
 * Buyer refund applications and the operations review that releases the channel refund.
 *
 * <p>Applying never moves money. It creates a pending request when the order is within seven days
 * of payment and the course progress is at most twenty percent; anything outside that window is
 * rejected with {@code 62007}. Operations must approve before the channel is called.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface RefundService {

    /**
     * Applies for a full refund of one paid order of the current buyer.
     *
     * <p>Repeating the call while a request is pending, approved or refunded returns that request
     * instead of creating another one. After a rejection or a channel failure the buyer may apply
     * again, which creates a new request with a new channel idempotency key.
     *
     * @param userId buyer id from the gateway header
     * @param orderId order primary key
     * @param request reason and optional amount
     * @return the in-progress or newly created request
     * @throws com.lp.common.exception.BizException when the order is absent, not owned, not paid,
     *     the amount is not the paid amount, or the admission rules are not met
     * @author Caspian
     * @since 0.1.0
     */
    RefundView apply(long userId, long orderId, ApplyRefundRequest request);

    /**
     * Returns one refund of the current buyer.
     *
     * @param userId buyer id from the gateway header
     * @param refundId refund primary key
     * @return refund state
     * @throws com.lp.common.exception.BizException when the refund is absent or owned by another
     *     buyer
     * @author Caspian
     * @since 0.1.0
     */
    RefundView get(long userId, long refundId);

    /**
     * Records an operations decision and, when approved, releases the channel refund.
     *
     * <p>The decision is claimed with a conditional update, so a duplicated review returns the
     * stored result. Rejection requires a reason and leaves the order paid and the entitlement
     * active. A channel failure stores the reason, returns the order to paid and keeps the
     * entitlement, so operations can retry manually.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason required rejection reason
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @param traceId request trace id carried by the audit intent
     * @return refund state after the decision
     * @throws com.lp.common.exception.BizException when the refund is absent, the reason is missing
     *     on rejection, or the request was already decided
     * @author Caspian
     * @since 0.1.0
     */
    RefundView review(
            long refundId,
            boolean approved,
            String rejectReason,
            long reviewerId,
            int operatorRole,
            String traceId);

    /**
     * Reads one page of refunds for the operations review list.
     *
     * @param status status filter, {@code null} for all statuses
     * @param pageNum one based page number
     * @param pageSize page size capped at 100
     * @return refund page
     * @author Caspian
     * @since 0.1.0
     */
    RefundAdminPageView list(Integer status, int pageNum, int pageSize);
}
