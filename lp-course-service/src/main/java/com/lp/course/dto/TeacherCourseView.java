package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * One course as shown in the teacher console.
 *
 * <p>{@code price} is converted to fen so the console never parses decimal strings. The request
 * carries yuan instead; the server owns that conversion. The review remark is present only after a
 * rejection, which tells the teacher what to fix before resubmitting.
 *
 * @param courseId course primary key
 * @param title course title
 * @param subtitle optional selling point
 * @param coverUrl cover image address
 * @param description optional long description
 * @param price proposed sale price in fen, the response unit of this path
 * @param originalPrice crossed out price in fen, {@code null} when unset
 * @param categoryId target category
 * @param level 1 beginner, 2 intermediate, 3 advanced
 * @param status 0 draft, 1 pending review, 2 published, 3 offline
 * @param reviewRemark rejection remark, {@code null} when never rejected
 * @param lessonCount number of live lessons
 * @param submittedAt last submit time, {@code null} when never submitted
 * @param updatedAt last modification time
 * @param sold {@code true} sold, {@code false} unsold, {@code null} unknown; only detail fills it
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherCourseView(
        long courseId,
        String title,
        String subtitle,
        String coverUrl,
        String description,
        long price,
        Long originalPrice,
        Long categoryId,
        int level,
        int status,
        String reviewRemark,
        long lessonCount,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        Boolean sold) {}
