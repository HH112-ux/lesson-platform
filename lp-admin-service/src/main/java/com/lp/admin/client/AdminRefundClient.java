package com.lp.admin.client;

import com.lp.admin.dto.AdminRefundPageView;
import com.lp.admin.dto.AdminRefundView;

/**
 * Port to the trade-service refund review API.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface AdminRefundClient {

    /**
     * Lists one page of refunds.
     *
     * @param status optional refund status
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of refunds
     * @author Caspian
     * @since 0.1.0
     */
    AdminRefundPageView page(Integer status, int pageNum, int pageSize);

    /**
     * Approves or rejects one pending refund.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason rejection reason, required when rejecting
     * @param reviewerId operations user id from the console session
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return refund state after the decision
     * @author Caspian
     * @since 0.1.0
     */
    AdminRefundView review(
            long refundId,
            boolean approved,
            String rejectReason,
            long reviewerId,
            int operatorRole);
}
