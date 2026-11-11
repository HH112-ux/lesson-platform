package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.domain.LessonUploadStatus;
import com.lp.course.dto.TeacherLessonView;
import com.lp.course.dto.TeacherPlayUrlView;
import com.lp.course.dto.TeacherUploadSessionRequest;
import com.lp.course.dto.TeacherUploadSessionView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.LessonUpload;
import com.lp.course.mapper.CourseReviewAuditMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.upload.LessonUploadObjectReader;
import com.lp.course.upload.LessonUploadObjectReader.DownloadedVideo;
import com.lp.course.upload.LessonUploadUrlSigner;
import com.lp.course.upload.LessonVideoMetadata;
import com.lp.course.upload.LessonVideoProbe;
import com.lp.course.upload.LessonVideoValidator;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

    private static final String OBJECT_KEY = "lesson-videos/77/501.mp4";

    @Mock private TeacherCourseMapper teacherCourseMapper;

    @Mock private LessonUploadMapper lessonUploadMapper;

    @Mock private CourseReviewAuditMapper courseReviewAuditMapper;

    @Mock private LessonUploadUrlSigner uploadSigner;

    @Mock private LessonUploadObjectReader uploadReader;

    @Mock private LessonVideoProbe videoProbe;

    @Mock private LessonVideoValidator videoValidator;

    private TeacherCourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new TeacherCourseServiceImpl(
                        teacherCourseMapper,
                        lessonUploadMapper,
                        courseReviewAuditMapper,
                        uploadSigner,
                        uploadReader,
                        new CourseUploadProperties(
                                Duration.ofMinutes(30),
                                Duration.ofMinutes(10),
                                MAX_SIZE,
                                "",
                                "",
                                "",
                                "",
                                ""),
                        videoProbe,
                        videoValidator);
    }

    @Test
    void createsSessionWithStableObjectKey() {
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class))).thenAnswer(rowWithId(501L));
        when(uploadSigner.presignPut(any(), any())).thenReturn("https://minio/put");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(501L, view.uploadId());
        assertTrue(view.expiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        verify(lessonUploadMapper).updateObjectKey(501L, "lesson-videos/77/501.mp4");
        verify(uploadSigner).presignPut("lesson-videos/77/501.mp4", Duration.ofMinutes(30));
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
        LessonUpload live = session(800L, LocalDateTime.now().plusMinutes(10));
        live.setExpectedSizeBytes(1024L);
        when(lessonUploadMapper.selectPendingByLessonId(77L)).thenReturn(live);
        when(uploadSigner.presignPut(any(), any())).thenReturn("https://minio/reused");

        TeacherUploadSessionView view = create(1024L);

        assertEquals(800L, view.uploadId());
        assertEquals("lesson-videos/77/800.mp4", view.objectKey());
        verify(lessonUploadMapper, never()).markExpired(anyLong());
        verify(lessonUploadMapper, never()).updateObjectKey(anyLong(), any());
    }

    @Test
    void reportsPersistedSizeWhenTheDeclarationDiffersOnReuse() {
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"));
        LessonUpload live = session(800L, LocalDateTime.now().plusMinutes(10));
        live.setExpectedSizeBytes(1024L);
        when(lessonUploadMapper.selectPendingByLessonId(77L)).thenReturn(live);
        when(uploadSigner.presignPut(any(), any())).thenReturn("https://minio/reused");

        TeacherUploadSessionView view = create(2048L);

        assertEquals(1024L, view.expectedSizeBytes());
    }

    @Test
    void expiresStaleSessionAndRetriesInsert() {
        givenEditableLesson();
        when(lessonUploadMapper.insert(any(LessonUpload.class)))
                .thenThrow(new DuplicateKeyException("uk_lesson_pending"))
                .thenAnswer(rowWithId(900L));
        when(lessonUploadMapper.selectPendingByLessonId(77L))
                .thenReturn(session(801L, LocalDateTime.now().minusMinutes(1)));
        when(uploadSigner.presignPut(any(), any())).thenReturn("https://minio/new");

        assertEquals(900L, create(1024L).uploadId());
        verify(lessonUploadMapper).markExpired(801L);
    }

    @Test
    void confirmsUploadAndWritesProbedFacts() {
        givenEditableLesson();
        when(lessonUploadMapper.selectById(501L)).thenReturn(pendingSession());
        when(uploadReader.statSize(OBJECT_KEY)).thenReturn(1024L);
        when(uploadReader.download(OBJECT_KEY)).thenReturn(downloaded());
        when(videoProbe.probe(any()))
                .thenReturn(new LessonVideoMetadata("isom", 90.4, "h264", 1280, 720, "aac"));
        when(lessonUploadMapper.confirm(
                        anyLong(), any(), any(), anyInt(), any(), anyInt(), anyInt(), anyLong(),
                        any()))
                .thenReturn(1);
        CourseLesson lesson = lesson();
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson);

        TeacherLessonView view = confirm(501L);

        assertEquals(77L, view.lessonId());
        assertTrue(view.hasVideo());
        assertEquals(90, view.durationSec());
        assertEquals("h264", view.videoCodec());
        assertEquals(1280, view.videoWidth());
        assertEquals(720, view.videoHeight());
        assertEquals(1024L, view.videoSizeBytes());
        verify(videoValidator).validate(any(LessonVideoMetadata.class));
        verify(lessonUploadMapper)
                .confirm(
                        eq(501L),
                        any(),
                        eq(OBJECT_KEY),
                        eq(90),
                        eq("h264"),
                        eq(1280),
                        eq(720),
                        eq(1024L),
                        eq("sha256-value"));
        verify(uploadReader).delete(any(DownloadedVideo.class));
    }

    @Test
    void returnsConfirmedLessonWithoutReadingTheObjectAgain() {
        givenEditableLesson();
        LessonUpload confirmed = pendingSession();
        confirmed.setStatus(LessonUploadStatus.CONFIRMED);
        when(lessonUploadMapper.selectById(501L)).thenReturn(confirmed);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson());

        assertEquals(77L, confirm(501L).lessonId());
        verify(uploadReader, never()).statSize(any());
        verify(uploadReader, never()).download(any());
        verify(videoProbe, never()).probe(any());
    }

    @Test
    void rejectsStoredSizeThatDiffersFromTheDeclaration() {
        givenEditableCourse();
        when(lessonUploadMapper.selectById(501L)).thenReturn(pendingSession());
        when(uploadReader.statSize(OBJECT_KEY)).thenReturn(2048L);

        BizException exception = assertThrows(BizException.class, () -> confirm(501L));

        assertEquals(61003, exception.getCode());
        verify(uploadReader, never()).download(any());
        verify(lessonUploadMapper, never())
                .confirm(
                        anyLong(), any(), any(), anyInt(), any(), anyInt(), anyInt(), anyLong(),
                        any());
    }

    @Test
    void rejectsExpiredSession() {
        givenEditableCourse();
        LessonUpload expired = pendingSession();
        expired.setSessionExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(lessonUploadMapper.selectById(501L)).thenReturn(expired);

        BizException exception = assertThrows(BizException.class, () -> confirm(501L));

        assertEquals(61007, exception.getCode());
        verify(uploadReader, never()).statSize(any());
    }

    @Test
    void reportsConflictWhenAConcurrentCallKeepsTheSessionPending() {
        givenEditableCourse();
        when(lessonUploadMapper.selectById(501L)).thenReturn(pendingSession());
        when(uploadReader.statSize(OBJECT_KEY)).thenReturn(1024L);
        when(uploadReader.download(OBJECT_KEY)).thenReturn(downloaded());
        when(videoProbe.probe(any()))
                .thenReturn(new LessonVideoMetadata("isom", 90.4, "h264", 1280, 720, "aac"));
        when(lessonUploadMapper.confirm(
                        anyLong(), any(), any(), anyInt(), any(), anyInt(), anyInt(), anyLong(),
                        any()))
                .thenReturn(0);

        BizException exception = assertThrows(BizException.class, () -> confirm(501L));

        assertEquals(61008, exception.getCode());
        verify(uploadReader).delete(any(DownloadedVideo.class));
    }

    @Test
    void signsPlaybackUrlForOwnedLesson() {
        givenEditableCourse();
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson());
        when(uploadSigner.presignGet(OBJECT_KEY, Duration.ofMinutes(10)))
                .thenReturn("https://minio/get");

        long before = Instant.now().plusSeconds(600).getEpochSecond();
        TeacherPlayUrlView view = service.playUrl(1001L, "TEACHER", 9L, 3L, 77L);

        assertEquals("https://minio/get", view.playUrl());
        assertEquals(600, view.expiresIn());
        assertTrue(view.expiresAt() >= before);
    }

    @Test
    void letsAdministratorSignAnyCourse() {
        givenCourse(0, 12L);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson());
        when(uploadSigner.presignGet(OBJECT_KEY, Duration.ofMinutes(10)))
                .thenReturn("https://minio/get");

        assertEquals("https://minio/get", service.playUrl(1001L, "ADMIN", 9L, 3L, 77L).playUrl());
    }

    @Test
    void refusesPlaybackForAnotherTeachersCourse() {
        givenCourse(0, 12L);

        BizException exception =
                assertThrows(
                        BizException.class, () -> service.playUrl(1001L, "TEACHER", 9L, 3L, 77L));

        assertEquals(40301, exception.getCode());
        verify(uploadSigner, never()).presignGet(any(), any());
    }

    @Test
    void refusesPlaybackWithoutAConfirmedVideo() {
        givenEditableLesson();

        BizException exception =
                assertThrows(
                        BizException.class, () -> service.playUrl(1001L, "TEACHER", 9L, 3L, 77L));

        assertEquals(40401, exception.getCode());
        verify(uploadSigner, never()).presignGet(any(), any());
    }

    private TeacherUploadSessionView create(long size) {
        return service.createUploadSession(
                1001L, "TEACHER", 9L, 3L, 77L, new TeacherUploadSessionRequest(size));
    }

    private TeacherLessonView confirm(long uploadId) {
        return service.confirmUpload(1001L, "TEACHER", 9L, 3L, 77L, uploadId);
    }

    private void givenEditableLesson() {
        givenCourse(0, 11L);
        CourseLesson lesson = new CourseLesson();
        lesson.setId(77L);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson);
    }

    private void givenEditableCourse() {
        givenCourse(0, 11L);
    }

    private void givenCourse(int status, long ownerTeacherId) {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(11L);
        Course course = new Course();
        course.setId(9L);
        course.setStatus(status);
        course.setOwnerTeacherId(ownerTeacherId);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course);
    }

    private LessonUpload pendingSession() {
        LessonUpload session = session(501L, LocalDateTime.now().plusMinutes(10));
        session.setStatus(LessonUploadStatus.PENDING);
        session.setCourseId(9L);
        session.setChapterId(3L);
        session.setLessonId(77L);
        session.setObjectKey(OBJECT_KEY);
        session.setExpectedSizeBytes(1024L);
        return session;
    }

    private DownloadedVideo downloaded() {
        return new DownloadedVideo(
                Path.of("/tmp/lp-lesson-video-test"), Path.of("/tmp/video.mp4"), "sha256-value");
    }

    private CourseLesson lesson() {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(77L);
        lesson.setChapterId(3L);
        lesson.setVideoUrl(OBJECT_KEY);
        lesson.setVideoDurationSec(90);
        lesson.setVideoCodec("h264");
        lesson.setVideoWidth(1280);
        lesson.setVideoHeight(720);
        lesson.setVideoSizeBytes(1024L);
        return lesson;
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
