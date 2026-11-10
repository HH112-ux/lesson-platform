package com.lp.course.dto;

/**
 * Create and edit payload of a lesson.
 *
 * <p>Video facts are owned by the upload confirmation endpoint. The two video fields are kept only
 * so a client that still sends them is rejected with {@code 40001} instead of being silently
 * ignored; a new lesson starts without a video.
 *
 * @param title lesson title, required on create, at most 120 characters
 * @param videoUrl must be omitted, rejected with {@code 40001} when present
 * @param durationSec must be omitted, rejected with {@code 40001} when present
 * @param isFree 1 free trial, 0 paid
 * @param sort display order, non-negative
 * @param status 1 visible, 0 hidden
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherLessonRequest(
        String title,
        String videoUrl,
        Integer durationSec,
        Integer isFree,
        Integer sort,
        Integer status) {}
