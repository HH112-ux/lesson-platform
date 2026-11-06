package com.lp.admin.dto;

import java.util.List;

/**
 * One page of the operations course review queue.
 *
 * @param records pending courses of this page
 * @param total total courses waiting for review
 * @param pageNum one based page number
 * @param pageSize page size
 * @author Caspian
 * @since 0.1.0
 */
public record AdminPendingCoursePageView(
        List<AdminPendingCourseView> records, long total, int pageNum, int pageSize) {}
