package com.lp.course.dto;

/**
 * Presigned playback URL handed to the teacher who owns the course; the object key never leaves the
 * server and the URL stops working after {@code expiresAt}.
 *
 * @param playUrl absolute presigned GET URL
 * @param expiresIn lifetime in seconds
 * @param expiresAt expiry as epoch seconds
 * @author Caspian
 * @since 0.10.0
 */
public record TeacherPlayUrlView(String playUrl, long expiresIn, long expiresAt) {}
