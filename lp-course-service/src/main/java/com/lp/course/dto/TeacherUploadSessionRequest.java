package com.lp.course.dto;

/**
 * Declaration a teacher sends before uploading a lesson video.
 *
 * @param expectedSizeBytes exact file size in bytes, positive and within the configured ceiling
 * @author Caspian
 * @since 0.7.0
 */
public record TeacherUploadSessionRequest(Long expectedSizeBytes) {}
