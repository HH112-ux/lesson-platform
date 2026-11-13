package com.lp.admin.dto;

/**
 * Learner counts returned by auth-service for the dashboard window.
 *
 * @param totalStudents live accounts holding the {@code STUDENT} role
 * @param newStudents learner accounts registered inside the window
 * @author Caspian
 * @since 0.21.0
 */
public record AdminUserMetrics(long totalStudents, long newStudents) {}
