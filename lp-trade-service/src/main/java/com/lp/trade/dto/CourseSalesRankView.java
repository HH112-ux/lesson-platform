package com.lp.trade.dto;

/**
 * One row of the dashboard course sales ranking.
 *
 * @param courseId course id
 * @param title course title captured on the order line
 * @param orderCount number of paid order lines of the course
 * @param revenueFen revenue of the course lines in fen
 * @author Caspian
 * @since 0.21.0
 */
public record CourseSalesRankView(long courseId, String title, long orderCount, long revenueFen) {}
