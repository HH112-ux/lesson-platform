package com.lp.course.service;

import com.lp.course.dto.TeacherChapterRequest;
import com.lp.course.dto.TeacherChapterView;
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.dto.TeacherLessonRequest;
import com.lp.course.dto.TeacherLessonView;
import com.lp.course.dto.TeacherUploadSessionRequest;
import com.lp.course.dto.TeacherUploadSessionView;
import java.util.List;

/**
 * Teacher console use cases for the courses a teacher owns.
 *
 * <p>Ownership is enforced in the service layer against {@code course.owner_teacher_id}; the caller
 * supplied user id is never trusted beyond the gateway-injected header.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface TeacherCourseService {

    /**
     * Lists one page of the caller's courses.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param status optional course status filter
     * @param page one based page number
     * @param size page size, capped at 100
     * @return one page of owned courses
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCoursePageView list(long userId, String roles, Integer status, int page, int size);

    /**
     * Creates a draft course owned by the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param request course form
     * @return the created course
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView create(long userId, String roles, TeacherCourseRequest request);

    /**
     * Edits a draft or offline course; editing an offline course sends it back to draft.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request course form, omitted fields keep their current value
     * @return the course after the update
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView update(
            long userId, String roles, long courseId, TeacherCourseRequest request);

    /**
     * Loads one course visible to the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return course detail
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView detail(long userId, String roles, long courseId);

    /**
     * Moves a draft or offline course into the review queue.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return the course in pending-review status
     * @author Caspian
     * @since 0.1.0
     */
    TeacherCourseView submit(long userId, String roles, long courseId);

    /**
     * Lists the chapters and lessons of one owned course, including hidden rows.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return chapters ordered by sort
     * @author Caspian
     * @since 0.1.0
     */
    List<TeacherChapterView> chapters(long userId, String roles, long courseId);

    /**
     * Creates a chapter in a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request chapter form
     * @return the created chapter
     * @author Caspian
     * @since 0.1.0
     */
    TeacherChapterView createChapter(
            long userId, String roles, long courseId, TeacherChapterRequest request);

    /**
     * Edits a chapter of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param request chapter form, omitted fields keep their current value
     * @return the chapter after the update
     * @author Caspian
     * @since 0.1.0
     */
    TeacherChapterView updateChapter(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            TeacherChapterRequest request);

    /**
     * Deletes a chapter and its lessons from a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @author Caspian
     * @since 0.1.0
     */
    void deleteChapter(long userId, String roles, long courseId, long chapterId);

    /**
     * Creates a lesson under a chapter of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param request lesson form
     * @return the created lesson
     * @author Caspian
     * @since 0.1.0
     */
    TeacherLessonView createLesson(
            long userId, String roles, long courseId, long chapterId, TeacherLessonRequest request);

    /**
     * Edits a lesson of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param request lesson form, omitted fields keep their current value
     * @return the lesson after the update
     * @author Caspian
     * @since 0.1.0
     */
    TeacherLessonView updateLesson(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            long lessonId,
            TeacherLessonRequest request);

    /**
     * Deletes a lesson of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @author Caspian
     * @since 0.1.0
     */
    void deleteLesson(long userId, String roles, long courseId, long chapterId, long lessonId);

    /**
     * Creates or reuses the pending upload session of a lesson and signs a PUT credential.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param request declared file size, may be {@code null}
     * @return session and presigned PUT URL
     * @author Caspian
     * @since 0.7.0
     */
    TeacherUploadSessionView createUploadSession(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            long lessonId,
            TeacherUploadSessionRequest request);

    /**
     * Confirms an uploaded lesson video and writes the video facts to the lesson.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param uploadId upload session primary key
     * @return the confirmed lesson, also returned when the confirmation is repeated
     * @author Caspian
     * @since 0.8.0
     */
    TeacherLessonView confirmUpload(
            long userId, String roles, long courseId, long chapterId, long lessonId, long uploadId);
}
