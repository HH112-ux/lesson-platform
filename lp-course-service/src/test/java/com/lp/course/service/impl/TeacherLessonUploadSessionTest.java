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
import org.mockito.stubbing.Answer;
import org.springframework.dao.DuplicateKeyException;

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
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class))).thenAnswer(rowWithId(501L));
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/put");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(501L, view.uploadId());
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
    void rejectsCourseThatIsNoLongerEditable() {
        givenCourse(2, 11L);

        BizException exception = assertThrows(BizException.class, () -> create(1024L));

        assertEquals(61008, exception.getCode());
        verify(lessonUploadMapper, never()).insert(any());
    }

    @Test
    void reusesPendingSessionOnDuplicateKey() {
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"));
        when(lessonUploadMapper.selectPendingByLessonId(77L))
                .thenReturn(session(800L, LocalDateTime.now().plusMinutes(10)));
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/reused");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(800L, view.uploadId());
        assertEquals("lesson-videos/77/800.mp4", view.objectKey());
        verify(lessonUploadMapper, never()).markExpired(anyLong());
        verify(lessonUploadMapper, never()).updateObjectKey(anyLong(), any());
    }

    @Test
    void expiresStaleSessionAndRetriesInsert() {
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"))
                .thenAnswer(rowWithId(900L));
        when(lessonUploadMapper.selectPendingByLessonId(77L))
                .thenReturn(session(801L, LocalDateTime.now().minusMinutes(1)));
        when(uploadUrlSigner.presignPut(any(), any())).thenReturn("https://minio/new");

        assertEquals(900L, create(1024L).uploadId());
        verify(lessonUploadMapper).markExpired(801L);
    }

    private TeacherUploadSessionView create(long size) {
        return service.createUploadSession(
                1001L, "TEACHER", 9L, 3L, 77L, new TeacherUploadSessionRequest(size));
    }

    private void givenEditableLesson() {
        givenCourse(0, 11L);
        CourseLesson lesson = new CourseLesson();
        lesson.setId(77L);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson);
    }

    private void givenCourse(int status, long ownerTeacherId) {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(11L);
        Course course = new Course();
        course.setId(9L);
        course.setStatus(status);
        course.setOwnerTeacherId(ownerTeacherId);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course);
    }

    private LessonUpload session(long id, LocalDateTime expiresAt) {
        LessonUpload session = new LessonUpload();
        session.setId(id);
        session.setObjectKey("lesson-videos/77/" + id + ".mp4");
        session.setSessionExpiresAt(expiresAt);
        return session;
    }

    private Answer<Integer> rowWithId(long id) {
        return invocation -> {
            LessonUpload row = invocation.getArgument(0);
            row.setId(id);
            return 1;
        };
    }
}
