package com.lp.auth.dto;

/**
 * Learner counts of a caller chosen window used by the operations dashboard.
 *
 * @param totalStudents live accounts holding the {@code STUDENT} role
 * @param newStudents learner accounts registered inside the window
 * @author Caspian
 * @since 0.21.0
 */
public record UserMetricsView(long totalStudents, long newStudents) {}
