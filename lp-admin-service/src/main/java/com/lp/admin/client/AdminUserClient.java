package com.lp.admin.client;

import com.lp.admin.dto.AdminUserPageView;

/**
 * Port to the auth-service account list.
 *
 * @author Caspian
 * @since 0.2.0
 */
public interface AdminUserClient {

    /**
     * Lists one page of accounts.
     *
     * @param keyword optional match on username, nickname or phone
     * @param status optional account status filter
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of accounts
     * @author Caspian
     * @since 0.2.0
     */
    AdminUserPageView page(String keyword, Integer status, int pageNum, int pageSize);
}
