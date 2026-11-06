package com.lp.admin.dto;

/**
 * One row of the best selling course ranking returned by trade-service.
 *
 * @param courseId course id
 * @param title course title captured on the order line
 * @param orderCount number of paid orders containing the course
 * @author Caspian
 * @since 0.2.0
 */
public record AdminCourseSaleView(long courseId, String title, long orderCount) {}
