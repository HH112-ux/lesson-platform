package com.lp.admin.dto;

/**
 * One row of the dashboard course ranking returned by trade-service.
 *
 * @param courseId course id
 * @param title course title captured on the order line
 * @param orderCount paid order line count of the course
 * @param revenueFen revenue of the course lines in fen
 * @author Caspian
 * @since 0.21.0
 */
public record AdminCourseSalesRankView(
        long courseId, String title, long orderCount, long revenueFen) {}
