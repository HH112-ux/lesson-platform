package com.lp.course.controller;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.result.Result;
import com.lp.common.web.TraceIds;
import com.lp.course.dto.TeacherChapterRequest;
import com.lp.course.dto.TeacherChapterView;
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.dto.TeacherLessonRequest;
import com.lp.course.dto.TeacherLessonView;
import com.lp.course.dto.TeacherUploadSessionRequest;
import com.lp.course.dto.TeacherUploadSessionView;
import com.lp.course.service.TeacherCourseService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher console course endpoints.
 *
 * <p>The gateway authenticates the caller and injects the roles; this controller rejects requests
 * without a trusted user id. Ownership is checked again in the service against {@code
 * course.owner_teacher_id}, so a {@code TEACHER} can never touch another teacher's course.
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/teacher/courses")
public class TeacherCourseController {

    private final TeacherCourseService teacherCourseService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param teacherCourseService teacher console use cases
     * @author Caspian
     * @since 0.1.0
     */
    public TeacherCourseController(TeacherCourseService teacherCourseService) {
        this.teacherCourseService = teacherCourseService;
    }

    /**
     * Lists the caller's courses.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param status optional status filter
     * @param pageNum one based page number, defaults to 1
     * @param pageSize page size, defaults to 20 and is capped at 100
     * @param request current request, used to resolve the trace id
     * @return one page of owned courses
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping
    public Result<TeacherCoursePageView> list(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.list(requireUser(userId), roles, status, pageNum, pageSize),
                TraceIds.resolve(request));
    }

    /**
     * Creates a draft course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param body course form
     * @param request current request, used to resolve the trace id
     * @return the created course
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping
    public Result<TeacherCourseView> create(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @RequestBody TeacherCourseRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.create(requireUser(userId), roles, body),
                TraceIds.resolve(request));
    }

    /**
     * Returns one course visible to the caller.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return course detail
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}")
    public Result<TeacherCourseView> detail(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.detail(requireUser(userId), roles, courseId),
                TraceIds.resolve(request));
    }

    /**
     * Edits a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param body course form, omitted fields keep their current value
     * @param request current request, used to resolve the trace id
     * @return the course after the update
     * @author Caspian
     * @since 0.1.0
     */
    @PutMapping("/{courseId}")
    public Result<TeacherCourseView> update(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @RequestBody TeacherCourseRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.update(requireUser(userId), roles, courseId, body),
                TraceIds.resolve(request));
    }

    /**
     * Submits a draft or offline course for review.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return the course in pending-review status
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{courseId}/submit")
    public Result<TeacherCourseView> submit(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.submit(requireUser(userId), roles, courseId),
                TraceIds.resolve(request));
    }

    /**
     * Lists the chapters and lessons of an owned course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param request current request, used to resolve the trace id
     * @return chapters ordered by sort, including hidden rows
     * @author Caspian
     * @since 0.1.0
     */
    @GetMapping("/{courseId}/chapters")
    public Result<List<TeacherChapterView>> chapters(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.chapters(requireUser(userId), roles, courseId),
                TraceIds.resolve(request));
    }

    /**
     * Creates a chapter in a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param body chapter form
     * @param request current request, used to resolve the trace id
     * @return the created chapter
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{courseId}/chapters")
    public Result<TeacherChapterView> createChapter(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @RequestBody TeacherChapterRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.createChapter(requireUser(userId), roles, courseId, body),
                TraceIds.resolve(request));
    }

    /**
     * Edits a chapter of a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param body chapter form, omitted fields keep their current value
     * @param request current request, used to resolve the trace id
     * @return the chapter after the update
     * @author Caspian
     * @since 0.1.0
     */
    @PutMapping("/{courseId}/chapters/{chapterId}")
    public Result<TeacherChapterView> updateChapter(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @RequestBody TeacherChapterRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.updateChapter(
                        requireUser(userId), roles, courseId, chapterId, body),
                TraceIds.resolve(request));
    }

    /**
     * Deletes a chapter and its lessons from a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param request current request, used to resolve the trace id
     * @return empty success payload
     * @author Caspian
     * @since 0.1.0
     */
    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public Result<Void> deleteChapter(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            HttpServletRequest request) {
        teacherCourseService.deleteChapter(requireUser(userId), roles, courseId, chapterId);
        return Result.ok(null, TraceIds.resolve(request));
    }

    /**
     * Creates a lesson under a chapter of a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param body lesson form
     * @param request current request, used to resolve the trace id
     * @return the created lesson
     * @author Caspian
     * @since 0.1.0
     */
    @PostMapping("/{courseId}/chapters/{chapterId}/lessons")
    public Result<TeacherLessonView> createLesson(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @RequestBody TeacherLessonRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.createLesson(
                        requireUser(userId), roles, courseId, chapterId, body),
                TraceIds.resolve(request));
    }

    /**
     * Edits a lesson of a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param body lesson form, omitted fields keep their current value
     * @param request current request, used to resolve the trace id
     * @return the lesson after the update
     * @author Caspian
     * @since 0.1.0
     */
    @PutMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public Result<TeacherLessonView> updateLesson(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @PathVariable("lessonId") long lessonId,
            @RequestBody TeacherLessonRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.updateLesson(
                        requireUser(userId), roles, courseId, chapterId, lessonId, body),
                TraceIds.resolve(request));
    }

    /**
     * Deletes a lesson of a draft or offline course.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param request current request, used to resolve the trace id
     * @return empty success payload
     * @author Caspian
     * @since 0.1.0
     */
    @DeleteMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public Result<Void> deleteLesson(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @PathVariable("lessonId") long lessonId,
            HttpServletRequest request) {
        teacherCourseService.deleteLesson(
                requireUser(userId), roles, courseId, chapterId, lessonId);
        return Result.ok(null, TraceIds.resolve(request));
    }

    /**
     * Issues a presigned PUT credential for the video of a lesson.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param body declared file size, omitted when the client sends no body
     * @param request current request, used to resolve the trace id
     * @return session and presigned PUT URL
     * @author Caspian
     * @since 0.7.0
     */
    @PostMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}/upload-sessions")
    public Result<TeacherUploadSessionView> createUploadSession(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @PathVariable("lessonId") long lessonId,
            @RequestBody(required = false) TeacherUploadSessionRequest body,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.createUploadSession(
                        requireUser(userId), roles, courseId, chapterId, lessonId, body),
                TraceIds.resolve(request));
    }

    /**
     * Confirms an uploaded lesson video and stores its probed facts.
     *
     * @param userId gateway injected user id
     * @param roles gateway injected roles, comma separated
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param uploadId upload session primary key
     * @param request current request, used to resolve the trace id
     * @return the confirmed lesson, also returned when the confirmation is repeated
     * @author Caspian
     * @since 0.8.0
     */
    @PostMapping(
            "/{courseId}/chapters/{chapterId}/lessons/{lessonId}/upload-sessions/{uploadId}/confirm")
    public Result<TeacherLessonView> confirmUpload(
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String roles,
            @PathVariable("courseId") long courseId,
            @PathVariable("chapterId") long chapterId,
            @PathVariable("lessonId") long lessonId,
            @PathVariable("uploadId") long uploadId,
            HttpServletRequest request) {
        return Result.ok(
                teacherCourseService.confirmUpload(
                        requireUser(userId), roles, courseId, chapterId, lessonId, uploadId),
                TraceIds.resolve(request));
    }

    /**
     * Rejects a request that reached the service without a trusted user id.
     *
     * @param userId gateway injected user id
     * @return the same id when present and positive
     * @author Caspian
     * @since 0.1.0
     */
    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
