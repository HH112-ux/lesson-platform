package com.lp.course.dto;

import java.util.List;

/**
 * One page of the teacher's own courses.
 *
 * @param items course rows, most recently updated first
 * @param total total matching courses
 * @param page one based page number
 * @param size requested page size
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherCoursePageView(
        List<TeacherCourseView> items, long total, int page, int size) {}
