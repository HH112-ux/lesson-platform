package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.client.TradeOrderClient;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.dto.TeacherChapterRequest;
import com.lp.course.dto.TeacherChapterView;
import com.lp.course.dto.TeacherLessonRequest;
import com.lp.course.dto.TeacherLessonView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.CourseReviewAuditMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.upload.LessonPlaybackKeyResolver;
import com.lp.course.upload.LessonTranscodeService;
import com.lp.course.upload.LessonUploadObjectReader;
import com.lp.course.upload.LessonUploadUrlSigner;
import com.lp.course.upload.LessonVideoProbe;
import com.lp.course.upload.LessonVideoValidator;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the teacher console chapter and lesson rules.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class TeacherChapterLessonServiceImplTest {

    @Mock private TeacherCourseMapper teacherCourseMapper;

    @Mock private LessonUploadMapper lessonUploadMapper;

    @Mock private CourseReviewAuditMapper courseReviewAuditMapper;

    @Mock private LessonUploadUrlSigner uploadSigner;

    @Mock private LessonUploadObjectReader uploadReader;

    @Mock private LessonVideoProbe videoProbe;

    @Mock private LessonVideoValidator videoValidator;

    @Mock private TradeOrderClient tradeOrderClient;
    @Mock private LessonTranscodeService transcodeService;

    @Mock private LessonPlaybackKeyResolver playbackKeyResolver;

    private TeacherCourseServiceImpl service;

    /**
     * Builds the service under test with mocked dependencies.
     *
     * @author Caspian
     * @since 0.1.0
     */
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
                                209_715_200L,
                                "",
                                "",
                                "",
                                "",
                                ""),
                        videoProbe,
                        videoValidator,
                        tradeOrderClient,
                        transcodeService,
                        playbackKeyResolver);
    }

    /**
     * Creating a chapter on a draft writes defaults and returns the row.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsChapterOnDraftCourse() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.insertChapter(any(CourseChapter.class)))
                .thenAnswer(
                        invocation -> {
                            CourseChapter row = invocation.getArgument(0);
                            row.setId(71L);
                            return 1;
                        });

        TeacherChapterView view =
                service.createChapter(
                        1001L, "TEACHER", 9L, new TeacherChapterRequest("Basics", 2, null));

        assertEquals(71L, view.chapterId());
        assertEquals(2, view.sort());
        assertEquals(1, view.status());
        verify(teacherCourseMapper, never()).resetOfflineToDraft(9L);
    }

    /**
     * A chapter is checked against the course it claims to belong to.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsChapterFromAnotherCourse() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(null);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.updateChapter(
                                        1001L,
                                        "TEACHER",
                                        9L,
                                        71L,
                                        new TeacherChapterRequest("Renamed", null, null)));

        assertEquals(40401, exception.getCode());
    }

    /**
     * Deleting a chapter also removes the lessons it held.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void deletingChapterAlsoDeletesItsLessons() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(tradeOrderClient.paidOrderExists(9L)).thenReturn(false);

        service.deleteChapter(1001L, "TEACHER", 9L, 71L);

        verify(teacherCourseMapper).deleteChapter(9L, 71L);
        verify(teacherCourseMapper).deleteLessonsByChapter(9L, 71L);
    }

    /**
     * A chapter of a sold course cannot be deleted, so its lessons stay reachable.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @Test
    void rejectsChapterDeletionWhenCourseIsSold() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(tradeOrderClient.paidOrderExists(9L)).thenReturn(true);

        BizException exception =
                assertThrows(
                        BizException.class, () -> service.deleteChapter(1001L, "TEACHER", 9L, 71L));

        assertEquals(61009, exception.getCode());
        verify(teacherCourseMapper, never()).deleteChapter(9L, 71L);
        verify(teacherCourseMapper, never()).deleteLessonsByChapter(9L, 71L);
    }

    /**
     * Video fields are refused on create because the upload confirmation owns them.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsClientVideoFieldsOnCreate() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.createLesson(
                                        1001L,
                                        "TEACHER",
                                        9L,
                                        71L,
                                        new TeacherLessonRequest(
                                                "Intro", "course/9/1.mp4", 600, 0, 1, null)));

        assertEquals(40001, exception.getCode());
        assertTrue(exception.getMessage().contains("upload confirmation"));
        verify(teacherCourseMapper, never()).insertLesson(any());
    }

    /**
     * A new lesson starts without a video and keeps the rest of the form.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsLessonWithoutVideo() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(teacherCourseMapper.insertLesson(any(CourseLesson.class)))
                .thenAnswer(
                        invocation -> {
                            CourseLesson row = invocation.getArgument(0);
                            row.setId(81L);
                            return 1;
                        });

        TeacherLessonView view =
                service.createLesson(
                        1001L,
                        "TEACHER",
                        9L,
                        71L,
                        new TeacherLessonRequest("Intro", null, null, 0, 1, 1));

        assertFalse(view.hasVideo());
        assertEquals(0, view.durationSec());
        assertEquals("", view.videoCodec());
        assertEquals(0, view.videoWidth());
        assertEquals(0, view.videoHeight());
        assertEquals(0L, view.videoSizeBytes());
        assertEquals("Intro", view.title());
    }

    /**
     * Video fields are refused on edit as well.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsClientVideoFieldsOnUpdate() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.updateLesson(
                                        1001L,
                                        "TEACHER",
                                        9L,
                                        71L,
                                        81L,
                                        new TeacherLessonRequest(
                                                null,
                                                "course/9/other.mp4",
                                                null,
                                                null,
                                                null,
                                                null)));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).updateLesson(any());
    }

    /**
     * Adding a lesson to an offline course sends the course back to draft.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void addingLessonToOfflineCourseResetsItToDraft() {
        givenOwnedCourse(3);
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(teacherCourseMapper.insertLesson(any(CourseLesson.class)))
                .thenAnswer(
                        invocation -> {
                            CourseLesson row = invocation.getArgument(0);
                            row.setId(81L);
                            return 1;
                        });
        when(teacherCourseMapper.resetOfflineToDraft(9L)).thenReturn(1);

        service.createLesson(
                1001L, "TEACHER", 9L, 71L, new TeacherLessonRequest("Intro", null, null, 1, 1, 1));

        verify(teacherCourseMapper).resetOfflineToDraft(9L);
    }

    /**
     * Content changes are refused while the course is under review.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsLessonChangeOnPendingCourse() {
        givenOwnedCourse(1);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.createLesson(
                                        1001L,
                                        "TEACHER",
                                        9L,
                                        71L,
                                        new TeacherLessonRequest("Intro", null, null, 0, 1, 1)));

        assertEquals(40901, exception.getCode());
    }

    /**
     * The chapter list nests lessons under their chapter in sort order.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void listsChaptersWithNestedLessons() {
        givenOwnedCourse(0);
        when(teacherCourseMapper.selectChapters(9L)).thenReturn(List.of(chapter(71L, 9L)));
        when(teacherCourseMapper.selectLessons(9L))
                .thenReturn(List.of(lesson(81L, 9L, 71L, 2), lesson(82L, 9L, 71L, 1)));

        List<TeacherChapterView> chapters = service.chapters(1001L, "TEACHER", 9L);

        assertEquals(1, chapters.size());
        assertEquals(2, chapters.get(0).lessons().size());
        assertEquals(81L, chapters.get(0).lessons().get(0).lessonId());
        assertEquals("h264", chapters.get(0).lessons().get(0).videoCodec());
        assertEquals(1280, chapters.get(0).lessons().get(0).videoWidth());
        assertEquals(720, chapters.get(0).lessons().get(0).videoHeight());
        assertEquals(2048L, chapters.get(0).lessons().get(0).videoSizeBytes());
    }

    private void givenOwnedCourse(int status) {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(course(9L, status));
    }

    private Course course(long id, int status) {
        Course course = new Course();
        course.setId(id);
        course.setOwnerTeacherId(55L);
        course.setTitle("Course");
        course.setCoverUrl("cover");
        course.setPrice(new BigDecimal("99.00"));
        course.setStatus(status);
        return course;
    }

    private CourseChapter chapter(long id, long courseId) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setTitle("Basics");
        chapter.setSort(1);
        chapter.setStatus(1);
        return chapter;
    }

    private CourseLesson lesson(long id, long courseId, long chapterId, int sort) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(id);
        lesson.setCourseId(courseId);
        lesson.setChapterId(chapterId);
        lesson.setTitle("Lesson " + id);
        lesson.setVideoUrl("course/9/" + id + ".mp4");
        lesson.setVideoDurationSec(600);
        lesson.setVideoCodec("h264");
        lesson.setVideoWidth(1280);
        lesson.setVideoHeight(720);
        lesson.setVideoSizeBytes(2048L);
        lesson.setIsFree(0);
        lesson.setSort(sort);
        lesson.setStatus(1);
        return lesson;
    }
}
