package com.lp.course.dto;

/**
 * Create and edit payload of a lesson.
 *
 * <p>On create the video object key and a positive duration are mandatory; on edit every field is
 * optional and the current value is kept when omitted.
 *
 * @param title lesson title, required on create, at most 120 characters
 * @param videoUrl video object key, required on create
 * @param durationSec video duration in seconds, required and positive on create
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
