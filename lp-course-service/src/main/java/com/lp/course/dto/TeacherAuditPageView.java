package com.lp.course.dto;

import java.util.List;

/**
 * One page of the teacher onboarding review queue.
 *
 * @param records matching profiles, newest first
 * @param total total matching rows before paging
 * @param pageNum one based page number
 * @param pageSize page size
 * @author Caspian
 * @since 0.5.0
 */
public record TeacherAuditPageView(
        List<TeacherAuditView> records, long total, int pageNum, int pageSize) {}
