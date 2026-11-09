package com.lp.admin.dto;

import java.util.List;

/**
 * One page of teacher applications.
 *
 * @param records matching applications, newest first
 * @param total total matching rows before paging
 * @param pageNum one based page number
 * @param pageSize page size
 * @author Caspian
 * @since 0.5.0
 */
public record AdminTeacherPageView(
        List<AdminTeacherView> records, long total, int pageNum, int pageSize) {}
