package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.client.TradeOrderClient;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseStatusCounts;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseReviewAudit;
import com.lp.course.entity.TeacherCourseStatusRow;
import com.lp.course.mapper.CourseReviewAuditMapper;
import com.lp.course.mapper.LessonContentCleanupMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the teacher console course rules.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class TeacherCourseServiceImplTest {

    @Mock private TeacherCourseMapper teacherCourseMapper;

    @Mock private LessonUploadMapper lessonUploadMapper;

    @Mock private CourseReviewAuditMapper courseReviewAuditMapper;

    @Mock private LessonUploadUrlSigner uploadSigner;

    @Mock private LessonUploadObjectReader uploadReader;

    @Mock private LessonVideoProbe videoProbe;

    @Mock private LessonVideoValidator videoValidator;

    @Mock private TradeOrderClient tradeOrderClient;
    @Mock private LessonTranscodeService transcodeService;
    @Mock private LessonContentCleanupMapper contentCleanupMapper;

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
                        playbackKeyResolver,
                        contentCleanupMapper);
    }

    /**
     * A teacher whose onboarding review is still pending cannot create a course.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsCreateWhenTeacherAuditPending() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(0);

        BizException exception =
                assertThrows(
                        BizException.class, () -> service.create(1001L, "TEACHER", validRequest()));

        assertEquals(61006, exception.getCode());
        verify(teacherCourseMapper, never()).insert(any());
    }

    /**
     * A rejected teacher cannot edit a course either, and no update reaches the database.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsUpdateWhenTeacherAuditRejected() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(2);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> service.update(1001L, "TEACHER", 9L, validRequest()));

        assertEquals(61006, exception.getCode());
        verify(teacherCourseMapper, never()).updateEditable(any());
    }

    /**
     * A teacher creates a draft owned by their own profile.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void createsDraftCourseForOwnTeacherProfile() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.insert(any(Course.class)))
                .thenAnswer(
                        invocation -> {
                            Course row = invocation.getArgument(0);
                            row.setId(9L);
                            return 1;
                        });
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));

        TeacherCourseView view = service.create(1001L, "TEACHER", validRequest());

        assertEquals(9L, view.courseId());
        assertEquals(0, view.status());
        assertEquals(9900L, view.price());
        verify(teacherCourseMapper).insert(any(Course.class));
    }

    /**
     * A caller without a teacher or admin role is rejected before any query runs.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsCallerWithoutTeacherRole() {
        BizException exception =
                assertThrows(
                        BizException.class, () -> service.create(1001L, "STUDENT", validRequest()));

        assertEquals(40301, exception.getCode());
        verifyNoInteractions(teacherCourseMapper);
    }

    /**
     * A teacher cannot edit a course owned by another teacher.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsEditingAnotherTeachersCourse() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 77L, 0));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> service.update(1001L, "TEACHER", 9L, validRequest()));

        assertEquals(40301, exception.getCode());
        verify(teacherCourseMapper, never()).updateEditable(any());
    }

    /**
     * A published course is read only for the teacher and cannot be edited directly.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsEditingPublishedCourse() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 2));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> service.update(1001L, "TEACHER", 9L, validRequest()));

        assertEquals(40901, exception.getCode());
    }

    /**
     * Editing an offline course succeeds and the mapper resets it to draft.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void editingOfflineCourseSendsItBackToDraft() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 3));
        when(teacherCourseMapper.updateEditable(any(Course.class))).thenReturn(1);
        when(teacherCourseMapper.selectById(9L))
                .thenReturn(draft(9L, 55L, 3))
                .thenReturn(draft(9L, 55L, 0));

        TeacherCourseView view = service.update(1001L, "TEACHER", 9L, validRequest());

        assertEquals(0, view.status());
    }

    /**
     * Clearing the crossed out price removes it instead of keeping the previous value.
     *
     * @author Caspian
     * @since 0.12.0
     */
    @Test
    void clearsOriginalPriceWhenRequested() {
        Course existing = draft(9L, 55L, 0);
        existing.setOriginalPrice(new BigDecimal("299.00"));
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.selectById(9L)).thenReturn(existing);
        when(teacherCourseMapper.updateEditable(any(Course.class))).thenReturn(1);
        TeacherCourseRequest request =
                new TeacherCourseRequest(3L, "Course", null, "cover", null, null, null, 1, true);

        service.update(1001L, "TEACHER", 9L, request);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(teacherCourseMapper).updateEditable(captor.capture());
        assertNull(captor.getValue().getOriginalPrice());
    }

    /**
     * A payload that both sets and clears the crossed out price is contradictory and rejected.
     *
     * @author Caspian
     * @since 0.12.0
     */
    @Test
    void rejectsSettingAndClearingOriginalPriceAtOnce() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, null, new BigDecimal("299.00"), 1, true);

        BizException exception =
                assertThrows(
                        BizException.class, () -> service.update(1001L, "TEACHER", 9L, request));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).updateEditable(any());
    }

    /**
     * Submission is refused while the course has no lesson with a video and duration.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsSubmissionWithoutPlayableLesson() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.countSubmittableLessons(9L)).thenReturn(0L);

        BizException exception =
                assertThrows(BizException.class, () -> service.submit(1001L, "TEACHER", 9L));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).submit(anyLong(), any());
    }

    /**
     * Submission is refused when a visible lesson still has no confirmed video.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsSubmissionWhenAnyVisibleLessonHasNoVideo() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.countSubmittableLessons(9L)).thenReturn(2L);
        when(teacherCourseMapper.countLessonsMissingVideo(9L)).thenReturn(1L);

        BizException exception =
                assertThrows(BizException.class, () -> service.submit(1001L, "TEACHER", 9L));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).submit(anyLong(), any());
    }

    /**
     * A draft with playable lessons moves to pending review.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void submitsDraftWithLessons() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.countSubmittableLessons(9L)).thenReturn(2L);
        when(teacherCourseMapper.submit(eq(9L), any())).thenReturn(1);
        when(teacherCourseMapper.selectById(9L))
                .thenReturn(draft(9L, 55L, 0))
                .thenReturn(draft(9L, 55L, 1));

        TeacherCourseView view = service.submit(1001L, "TEACHER", 9L);

        assertEquals(1, view.status());
        verify(teacherCourseMapper).submit(eq(9L), any());
    }

    /**
     * A pending course cannot be submitted twice.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsSubmittingPendingCourse() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 1));

        BizException exception =
                assertThrows(BizException.class, () -> service.submit(1001L, "TEACHER", 9L));

        assertEquals(40901, exception.getCode());
    }

    /**
     * Withdrawing a pending course returns it to draft and records who did it.
     *
     * @author Caspian
     * @since 0.13.0
     */
    @Test
    void withdrawsOwnPendingCourse() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L))
                .thenReturn(draft(9L, 55L, 1))
                .thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.withdraw(9L)).thenReturn(1);

        TeacherCourseView view = service.withdraw(1001L, "TEACHER", 9L);

        assertEquals(0, view.status());
        ArgumentCaptor<CourseReviewAudit> captor = ArgumentCaptor.forClass(CourseReviewAudit.class);
        verify(courseReviewAuditMapper).insert(captor.capture());
        CourseReviewAudit audit = captor.getValue();
        assertEquals(3, audit.getAction());
        assertEquals(1, audit.getFromStatus());
        assertEquals(0, audit.getToStatus());
        assertEquals(1001L, audit.getReviewerId());
        assertNotNull(audit.getCreatedAt());
    }

    /**
     * Only pending courses can be withdrawn; published or draft rows are rejected.
     *
     * @author Caspian
     * @since 0.13.0
     */
    @Test
    void rejectsWithdrawWhenCourseIsNotPending() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 2));

        BizException exception =
                assertThrows(BizException.class, () -> service.withdraw(1001L, "TEACHER", 9L));

        assertEquals(40901, exception.getCode());
        verify(teacherCourseMapper, never()).withdraw(anyLong());
    }

    /**
     * Operations cannot withdraw a teacher's course on the teacher's behalf.
     *
     * @author Caspian
     * @since 0.13.0
     */
    @Test
    void rejectsAdminWithdrawOnBehalf() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(null);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 1));

        BizException exception =
                assertThrows(BizException.class, () -> service.withdraw(1001L, "ADMIN", 9L));

        assertEquals(40301, exception.getCode());
        verify(teacherCourseMapper, never()).withdraw(anyLong());
    }

    /**
     * A concurrent review decision leaves nothing to withdraw and reports a state conflict.
     *
     * @author Caspian
     * @since 0.13.0
     */
    @Test
    void rejectsWithdrawWhenStatusAlreadyChanged() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 1));
        when(teacherCourseMapper.withdraw(9L)).thenReturn(0);

        BizException exception =
                assertThrows(BizException.class, () -> service.withdraw(1001L, "TEACHER", 9L));

        assertEquals(40901, exception.getCode());
        verify(courseReviewAuditMapper, never()).insert(any(CourseReviewAudit.class));
    }

    /**
     * A negative price is rejected before the insert.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsNegativePrice() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, new BigDecimal("-1"), null, 1, null);

        BizException exception =
                assertThrows(BizException.class, () -> service.create(1001L, "TEACHER", request));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).insert(any());
    }

    /**
     * A price with three decimals is rejected instead of being rounded to two.
     *
     * @author Caspian
     * @since 0.4.0
     */
    @Test
    void rejectsPriceWithMoreThanTwoDecimals() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, new BigDecimal("9.999"), null, 1, null);

        BizException exception =
                assertThrows(BizException.class, () -> service.create(1001L, "TEACHER", request));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).insert(any());
    }

    /**
     * A price that overflows {@code DECIMAL(10,2)} is rejected before reaching the database.
     *
     * @author Caspian
     * @since 0.4.0
     */
    @Test
    void rejectsPriceAboveColumnMaximum() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectAuditStatusByTeacherId(55L)).thenReturn(1);
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L,
                        "Course",
                        null,
                        "cover",
                        null,
                        new BigDecimal("100000000.00"),
                        null,
                        1,
                        null);

        BizException exception =
                assertThrows(BizException.class, () -> service.create(1001L, "TEACHER", request));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).insert(any());
    }

    /**
     * The list is always filtered by the caller's teacher profile.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void listFiltersByCallerTeacherProfile() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.countStatusByOwner(55L)).thenReturn(statusRow(1, 0, 2, 3));
        when(teacherCourseMapper.countByOwner(55L, null)).thenReturn(1L);
        when(teacherCourseMapper.selectPageByOwner(55L, null, 0L, 20L))
                .thenReturn(List.of(draft(9L, 55L, 0)));

        TeacherCoursePageView page = service.list(1001L, "TEACHER", null, 1, 20);

        assertEquals(1, page.total());
        assertEquals(9L, page.items().get(0).courseId());
        assertNull(page.items().get(0).reviewRemark());
        assertEquals(new TeacherCourseStatusCounts(1, 0, 2, 3), page.counts());
    }

    /**
     * Deletion is refused unless the trade service confirms the course was never sold.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @Test
    void rejectsLessonDeletionUnlessCourseIsConfirmedUnsold() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(tradeOrderClient.paidOrderExists(9L)).thenReturn(true, (Boolean) null);

        BizException sold =
                assertThrows(
                        BizException.class,
                        () -> service.deleteLesson(1001L, "TEACHER", 9L, 71L, 81L));
        BizException unknown =
                assertThrows(
                        BizException.class,
                        () -> service.deleteLesson(1001L, "TEACHER", 9L, 71L, 81L));

        assertEquals(61009, sold.getCode());
        assertEquals(50401, unknown.getCode());
        verify(teacherCourseMapper, never()).deleteLesson(anyLong(), anyLong(), anyLong());
        verify(contentCleanupMapper, never()).insertLessonCleanup(anyLong(), anyLong(), anyLong());
    }

    /**
     * An unsold course still allows deleting a lesson.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @Test
    void deletesLessonWhenCourseIsNotSold() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 0));
        when(teacherCourseMapper.selectChapter(9L, 71L)).thenReturn(chapter(71L, 9L));
        when(tradeOrderClient.paidOrderExists(9L)).thenReturn(false);
        when(teacherCourseMapper.deleteLesson(9L, 71L, 81L)).thenReturn(1);

        service.deleteLesson(1001L, "TEACHER", 9L, 71L, 81L);

        verify(contentCleanupMapper).insertLessonCleanup(9L, 71L, 81L);
        verify(teacherCourseMapper).deleteLesson(9L, 71L, 81L);
    }

    private CourseChapter chapter(long id, long courseId) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        return chapter;
    }

    /**
     * Builds an aggregated counter row.
     *
     * @param draft draft count
     * @param pending pending count
     * @param published published count
     * @param offline offline count
     * @return counter row
     * @author Caspian
     * @since 0.2.0
     */
    private TeacherCourseStatusRow statusRow(int draft, int pending, int published, int offline) {
        TeacherCourseStatusRow row = new TeacherCourseStatusRow();
        row.setDraft(draft);
        row.setPending(pending);
        row.setPublished(published);
        row.setOffline(offline);
        return row;
    }

    /**
     * Builds a course row.
     *
     * @param id course id
     * @param ownerTeacherId owning teacher
     * @param status course status
     * @return row
     * @author Caspian
     * @since 0.1.0
     */
    private Course draft(long id, long ownerTeacherId, int status) {
        Course course = new Course();
        course.setId(id);
        course.setOwnerTeacherId(ownerTeacherId);
        course.setCategoryId(3L);
        course.setTitle("Course " + id);
        course.setCoverUrl("cover-" + id);
        course.setPrice(new BigDecimal("99.00"));
        course.setLevel(1);
        course.setStatus(status);
        course.setLessonCount(2L);
        return course;
    }

    /**
     * Builds a valid create payload.
     *
     * @return request with every required field present
     * @author Caspian
     * @since 0.1.0
     */
    private TeacherCourseRequest validRequest() {
        return new TeacherCourseRequest(
                3L,
                "Course",
                "Subtitle",
                "cover",
                "Description",
                new BigDecimal("99.00"),
                null,
                1,
                null);
    }
}
