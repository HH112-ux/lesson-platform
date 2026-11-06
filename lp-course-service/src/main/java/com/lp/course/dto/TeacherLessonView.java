package com.lp.course.dto;

/**
 * One lesson inside a chapter of the teacher console.
 *
 * @param lessonId lesson primary key
 * @param chapterId owning chapter
 * @param title lesson title
 * @param videoUrl video object key
 * @param durationSec video duration in seconds
 * @param free whether the lesson is a free trial
 * @param sort display order
 * @param status 1 visible, 0 hidden
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherLessonView(
        long lessonId,
        long chapterId,
        String title,
        String videoUrl,
        int durationSec,
        boolean free,
        int sort,
        int status) {}
