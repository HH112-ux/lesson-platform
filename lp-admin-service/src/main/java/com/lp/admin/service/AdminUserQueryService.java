package com.lp.admin.service;

import com.lp.admin.client.AdminUserClient;
import com.lp.admin.dto.AdminUserPageView;
import org.springframework.stereotype.Service;

/**
 * Delegating use case for the console account list.
 *
 * <p>Paging is clamped before the call so an out of range page never reaches auth-service.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class AdminUserQueryService {

    /** Maximum page size accepted from the console. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserClient adminUserClient;

    /**
     * Creates the service with its auth-service port.
     *
     * @param adminUserClient account list API
     * @author Caspian
     * @since 0.2.0
     */
    public AdminUserQueryService(AdminUserClient adminUserClient) {
        this.adminUserClient = adminUserClient;
    }

    /**
     * Lists one page of accounts with clamped paging.
     *
     * @param keyword optional match on username, nickname or phone
     * @param status optional account status filter
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of accounts
     * @author Caspian
     * @since 0.2.0
     */
    public AdminUserPageView page(String keyword, Integer status, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return adminUserClient.page(keyword, status, safePage, safeSize);
    }
}
