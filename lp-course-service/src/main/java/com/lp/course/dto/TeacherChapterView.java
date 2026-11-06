package com.lp.course.dto;

import java.util.List;

/**
 * One chapter with its lessons, as shown in the teacher console.
 *
 * @param chapterId chapter primary key
 * @param title chapter title
 * @param sort display order
 * @param status 1 visible, 0 hidden
 * @param lessons lessons ordered by sort
 * @author Caspian
 * @since 0.1.0
 */
public record TeacherChapterView(
        long chapterId, String title, int sort, int status, List<TeacherLessonView> lessons) {}
