package com.lp.admin.dto;

import java.util.List;

/**
 * One page of unified audit entries returned to the console.
 *
 * @param records audit entries, newest decision first
 * @param total total rows matching the filter
 * @param pageNum one based page number
 * @param pageSize page size used for the query
 * @author Caspian
 * @since 0.6.0
 */
public record AdminAuditLogPageView(
        List<AdminAuditLogView> records, long total, int pageNum, int pageSize) {}
