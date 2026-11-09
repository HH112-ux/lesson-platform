package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseStatusCounts;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.entity.Course;
import com.lp.course.entity.TeacherCourseStatusRow;
import com.lp.course.mapper.TeacherCourseMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private TeacherCourseServiceImpl service;

    /**
     * Builds the service under test with a mocked mapper.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        service = new TeacherCourseServiceImpl(teacherCourseMapper);
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
        when(teacherCourseMapper.selectById(9L)).thenReturn(draft(9L, 55L, 3));
        when(teacherCourseMapper.updateEditable(any(Course.class))).thenReturn(1);
        when(teacherCourseMapper.selectById(9L))
                .thenReturn(draft(9L, 55L, 3))
                .thenReturn(draft(9L, 55L, 0));

        TeacherCourseView view = service.update(1001L, "TEACHER", 9L, validRequest());

        assertEquals(0, view.status());
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
     * A negative price is rejected before the insert.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsNegativePrice() {
        when(teacherCourseMapper.selectTeacherIdByUser(1001L)).thenReturn(55L);
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, new BigDecimal("-1"), null, 1);

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
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, new BigDecimal("9.999"), null, 1);

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
        TeacherCourseRequest request =
                new TeacherCourseRequest(
                        3L, "Course", null, "cover", null, new BigDecimal("100000000.00"), null, 1);

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
                3L, "Course", "Subtitle", "cover", "Description", new BigDecimal("99.00"), null, 1);
    }
}
