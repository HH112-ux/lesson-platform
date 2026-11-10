package com.lp.course.dto;

/**
 * One lesson inside a chapter of the teacher console.
 *
 * @param lessonId lesson primary key
 * @param chapterId owning chapter
 * @param title lesson title
 * @param videoUrl video object key, empty until the upload is confirmed
 * @param durationSec video duration in seconds, zero until the upload is confirmed
 * @param videoCodec video codec written by the confirm endpoint
 * @param videoWidth video width in pixels, zero until confirmed
 * @param videoHeight video height in pixels, zero until confirmed
 * @param videoSizeBytes stored object size in bytes, zero until confirmed
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
        String videoCodec,
        int videoWidth,
        int videoHeight,
        long videoSizeBytes,
        boolean free,
        int sort,
        int status) {}
