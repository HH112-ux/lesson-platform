package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.dto.TeacherUploadSessionRequest;
import com.lp.course.dto.TeacherUploadSessionView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.LessonUpload;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.upload.LessonUploadUrlSigner;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Unit tests for the presigned lesson upload session rules.
 *
 * @author Caspian
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class TeacherLessonUploadSessionTest {

    private static final long MAX_SIZE = 209_715_200L;

    @Mock private TeacherCourseMapper teacherCourseMapper;

    @Mock private LessonUploadMapper lessonUploadMapper;

    @Mock private LessonUploadUrlSigner uploadUrlSigner;

    private TeacherCourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new TeacherCourseServiceImpl(
                        teacherCourseMapper,
                        lessonUploadMapper,
                        uploadUrlSigner,
                        new CourseUploadProperties(
                                Duration.ofMinutes(30), MAX_SIZE, "", "", "", "", ""));
    }

    @Test
    void createsSessionWithStableObjectKey() {
        givenLessonOfCourse(0);
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenAnswer(
                        invocation -> {
                            LessonUpload row = invocation.getArgument(0);
                            row.setId(501L);
                            return 1;
                        });
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/put");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(501L, view.uploadId());
        assertEquals("lesson-videos/77/501.mp4", view.objectKey());
        assertEquals("https://minio/put", view.uploadUrl());
        assertTrue(view.expiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        verify(lessonUploadMapper).updateObjectKey(501L, "lesson-videos/77/501.mp4");
        verify(uploadUrlSigner).presignPut("lesson-videos/77/501.mp4", Duration.ofMinutes(30));
    }

    @Test
    void rejectsOversizedDeclaration() {
        BizException exception = assertThrows(BizException.class, () -> create(MAX_SIZE + 1));

        assertEquals(40001, exception.getCode());
        verify(lessonUploadMapper, never()).insert(any());
    }

    @Test
    void rejectsCourseOfAnotherTeacher() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(11L);
        Course course = course(0);
        course.setOwnerTeacherId(12L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course);

        BizException exception = assertThrows(BizException.class, () -> create(1024L));

        assertEquals(40301, exception.getCode());
        verify(lessonUploadMapper, never()).insert(any());
    }

    @Test
    void rejectsCourseThatIsNoLongerEditable() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(11L);
        Course course = course(2);
        course.setOwnerTeacherId(11L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course);

        BizException exception = assertThrows(BizException.class, () -> create(1024L));

        assertEquals(61008, exception.getCode());
        verify(lessonUploadMapper, never()).insert(any());
    }

    @Test
    void reusesPendingSessionOnDuplicateKey() {
        givenLessonOfCourse(0);
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"));
        LessonUpload existing = new LessonUpload();
        existing.setId(800L);
        existing.setObjectKey("lesson-videos/77/800.mp4");
        existing.setSessionExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(lessonUploadMapper.selectPendingByLessonId(77L)).thenReturn(existing);
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/reused");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(800L, view.uploadId());
        assertEquals("lesson-videos/77/800.mp4", view.objectKey());
        verify(lessonUploadMapper, never()).markExpired(anyLong());
        verify(lessonUploadMapper, never()).updateObjectKey(anyLong(), any());
    }

    @Test
    void expiresStaleSessionAndRetriesInsert() {
        givenLessonOfCourse(0);
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"))
                .thenAnswer(
                        invocation -> {
                            LessonUpload row = invocation.getArgument(0);
                            row.setId(900L);
                            return 1;
                        });
        LessonUpload stale = new LessonUpload();
        stale.setId(801L);
        stale.setSessionExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(lessonUploadMapper.selectPendingByLessonId(77L)).thenReturn(stale);
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/new");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(900L, view.uploadId());
        verify(lessonUploadMapper).markExpired(801L);
    }

    private TeacherUploadSessionView create(long size) {
        return service.createUploadSession(
                1001L, "TEACHER", 9L, 3L, 77L, new TeacherUploadSessionRequest(size));
    }

    private void givenLessonOfCourse(int courseStatus) {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(11L);
        Course course = course(courseStatus);
        course.setOwnerTeacherId(11L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course);
        CourseLesson lesson = new CourseLesson();
        lesson.setId(77L);
        lesson.setCourseId(9L);
        lesson.setChapterId(3L);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson);
    }

    private Course course(int status) {
        Course course = new Course();
        course.setId(9L);
        course.setStatus(status);
        return course;
    }
}
