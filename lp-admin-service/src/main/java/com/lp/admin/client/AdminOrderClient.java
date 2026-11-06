package com.lp.admin.client;

import com.lp.admin.dto.AdminOrderPageView;
import java.time.LocalDate;

/**
 * Port to the trade-service order list.
 *
 * @author Caspian
 * @since 0.2.0
 */
public interface AdminOrderClient {

    /**
     * Lists one page of orders.
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
    AdminOrderPageView page(
            Integer status,
            Integer channel,
            String orderNo,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int pageNum,
            int pageSize);
}
