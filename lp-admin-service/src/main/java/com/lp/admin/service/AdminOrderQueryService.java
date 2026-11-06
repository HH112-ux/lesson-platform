package com.lp.admin.service;

import com.lp.admin.client.AdminOrderClient;
import com.lp.admin.dto.AdminOrderPageView;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Delegating use case for the console order list.
 *
 * <p>Paging is clamped before the call so an out of range page never reaches trade-service.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class AdminOrderQueryService {

    /** Maximum page size accepted from the console. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminOrderClient adminOrderClient;

    /**
     * Creates the service with its trade-service port.
     *
     * @param adminOrderClient order list API
     * @author Caspian
     * @since 0.2.0
     */
    public AdminOrderQueryService(AdminOrderClient adminOrderClient) {
        this.adminOrderClient = adminOrderClient;
    }

    /**
     * Lists one page of orders with clamped paging.
     *
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param dateFrom optional inclusive creation date
     * @param dateTo optional inclusive creation date upper bound
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of orders
     * @author Caspian
     * @since 0.2.0
     */
    public AdminOrderPageView page(
            Integer status,
            Integer channel,
            String orderNo,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int pageNum,
            int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return adminOrderClient.page(
                status, channel, orderNo, userId, dateFrom, dateTo, safePage, safeSize);
    }
}
