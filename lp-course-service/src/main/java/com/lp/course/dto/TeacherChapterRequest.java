package com.lp.course.dto;

/**
 * Create and edit payload of a chapter.
 *
 * <p>On edit every field is optional and the current value is kept when omitted.
 *
 * @param title chapter title, required on create, at most 120 characters
 * @param sort display order, non-negative
 * @param status 1 visible, 0 hidden
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherChapterRequest(String title, Integer sort, Integer status) {}
