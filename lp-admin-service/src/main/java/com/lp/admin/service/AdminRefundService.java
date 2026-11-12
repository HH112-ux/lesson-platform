package com.lp.admin.service;

import com.lp.admin.client.AdminRefundClient;
import com.lp.admin.dto.AdminChannelRefundStatusView;
import com.lp.admin.dto.AdminRefundPageView;
import com.lp.admin.dto.AdminRefundView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Delegating use case for the console refund review queue.
 *
 * <p>Paging is clamped before the call and a rejection without a reason is refused locally, so the
 * browser cannot reach trade-service with a meaningless decision.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AdminRefundService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminRefundClient adminRefundClient;

    /**
     * Creates the service with its trade-service port.
     *
     * @param adminRefundClient refund review API
     * @author Caspian
     * @since 0.1.0
     */
    public AdminRefundService(AdminRefundClient adminRefundClient) {
        this.adminRefundClient = adminRefundClient;
    }

    /**
     * Lists one page of refunds with clamped paging.
     *
     * @param status optional refund status
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of refunds
     * @author Caspian
     * @since 0.1.0
     */
    public AdminRefundPageView page(Integer status, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return adminRefundClient.page(status, safePage, safeSize);
    }

    /**
     * Approves or rejects one pending refund.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason rejection reason, required when rejecting
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return refund state after the decision
     * @throws BizException with {@code 40001} when a rejection has no reason
     * @author Caspian
     * @since 0.1.0
     */
    public AdminRefundView review(
            long refundId,
            boolean approved,
            String rejectReason,
            long reviewerId,
            int operatorRole) {
        if (!approved && (rejectReason == null || rejectReason.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "rejectReason is required");
        }
        return adminRefundClient.review(refundId, approved, rejectReason, reviewerId, operatorRole);
    }

    /**
     * Reads one refund back from the channel for the console detail panel.
     *
     * <p>Read-only: the console calls it to explain a stuck refund and never to change it.
     *
     * @param refundId refund primary key
     * @return channel snapshot plus the local reconciliation bookkeeping
     * @author Caspian
     * @since 0.9.0
     */
    public AdminChannelRefundStatusView channelStatus(long refundId) {
        return adminRefundClient.channelStatus(refundId);
    }
}
