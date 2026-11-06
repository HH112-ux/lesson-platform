package com.lp.course.dto;

import java.math.BigDecimal;

/**
 * Create and edit payload of the teacher course form.
 *
 * <p>All fields are optional on edit; the service keeps the previous value when a field is omitted.
 * On create the picture is stricter: category, title, cover and price are required.
 *
 * @param categoryId target category, required on create
 * @param title course title, at most 120 characters
 * @param subtitle optional selling point, at most 200 characters
 * @param coverUrl cover image address, required on create
 * @param description optional long description
 * @param price proposed sale price in yuan, never negative
 * @param originalPrice optional crossed out price in yuan, never negative
 * @param level difficulty, 1 beginner, 2 intermediate, 3 advanced
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherCourseRequest(
        Long categoryId,
        String title,
        String subtitle,
        String coverUrl,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer level) {}
