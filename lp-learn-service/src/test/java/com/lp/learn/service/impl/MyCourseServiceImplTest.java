package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.learn.client.CourseInternalClient;
import com.lp.learn.client.CourseSnapshot;
import com.lp.learn.domain.EntitlementStatus;
import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.LearningProgressMapper;
import com.lp.learn.mapper.UserEntitlementMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for my-courses; persistence and course-service are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class MyCourseServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    private static final long OTHER_COURSE_ID = 8L;

    @Mock private UserEntitlementMapper userEntitlementMapper;

    @Mock private LearningProgressMapper learningProgressMapper;

    @Mock private CourseInternalClient courseInternalClient;

    private MyCourseServiceImpl myCourseService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        myCourseService =
                new MyCourseServiceImpl(
                        userEntitlementMapper, learningProgressMapper, courseInternalClient);
    }

    /**
     * A student without entitlements gets an empty page and no upstream call.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void noEntitlementReturnsEmptyPage() {
        when(userEntitlementMapper.countAllByUser(USER_ID)).thenReturn(0L);

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verifyNoInteractions(courseInternalClient, learningProgressMapper);
    }

    /**
     * An owned course without progress reports zero percent and no resume point.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void purchasedCourseWithoutProgressReportsEmpty() {
        stubEntitlement();
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(List.of());

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        MyCourseView view = page.getRecords().get(0);
        assertEquals(COURSE_ID, view.getCourseId());
        assertEquals("Spring Boot 实战", view.getTitle());
        assertEquals("cover.png", view.getCoverUrl());
        assertEquals(0, view.getProgressPercent());
        assertNull(view.getLastLessonId());
    }

    /**
     * One completed lesson out of three rounds to 33 percent.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void partialCompletionRoundsHalfUp() {
        stubEntitlement();
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(
                        List.of(
                                progress(11L, 1, 0, 1),
                                progress(10L, 2, 30, 0),
                                progress(12L, 3, 0, 0)));

        MyCourseView view = myCourseService.myCourses(USER_ID, 1, 20).getRecords().get(0);

        assertEquals(33, view.getProgressPercent());
        assertEquals(11L, view.getLastLessonId());
    }

    /**
     * Every lesson completed reports 100 percent.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void allLessonsCompletedReportsHundredPercent() {
        stubEntitlement();
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(
                        List.of(
                                progress(1L, 1, 0, 1),
                                progress(2L, 1, 0, 1),
                                progress(3L, 1, 0, 1)));

        MyCourseView view = myCourseService.myCourses(USER_ID, 1, 20).getRecords().get(0);

        assertEquals(100, view.getProgressPercent());
        assertEquals(1L, view.getLastLessonId());
    }

    /**
     * Progress rows of another course never leak into the current course.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void progressDoesNotLeakAcrossCourses() {
        UserEntitlement other = entitlement();
        other.setCourseId(OTHER_COURSE_ID);
        when(userEntitlementMapper.countAllByUser(USER_ID)).thenReturn(2L);
        when(userEntitlementMapper.selectAllByUser(eq(USER_ID), eq(0L), eq(20L)))
                .thenReturn(List.of(entitlement(), other));
        when(courseInternalClient.courses(anyList()))
                .thenReturn(
                        List.of(
                                new CourseSnapshot(COURSE_ID, "课程 A", "a.png", 4),
                                new CourseSnapshot(OTHER_COURSE_ID, "课程 B", "b.png", 2)));
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(
                        List.of(
                                progress(OTHER_COURSE_ID, 21L, 1),
                                progress(OTHER_COURSE_ID, 22L, 1)));

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        MyCourseView first = page.getRecords().get(0);
        MyCourseView second = page.getRecords().get(1);
        assertEquals(0, first.getProgressPercent());
        assertNull(first.getLastLessonId());
        assertEquals(100, second.getProgressPercent());
        assertEquals(21L, second.getLastLessonId());
    }

    /**
     * The resume point is the most recently updated row, not the highest lesson id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void lastLessonIdUsesLatestReport() {
        stubEntitlement(10);
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(
                        List.of(
                                progress(4L, 3, 200, 0, LocalDateTime.of(2026, 3, 2, 10, 0)),
                                progress(2L, 3, 90, 0, LocalDateTime.of(2026, 3, 1, 10, 0))));

        MyCourseView view = myCourseService.myCourses(USER_ID, 1, 20).getRecords().get(0);

        assertEquals(4L, view.getLastLessonId());
        assertEquals(0, view.getProgressPercent());
    }

    /**
     * A refunded course stays in my-courses, marked as refunded, and keeps its progress.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void refundedCourseIsMarkedAndKeepsProgress() {
        UserEntitlement refunded = entitlement();
        refunded.setStatus(EntitlementStatus.REVOKED);
        when(userEntitlementMapper.countAllByUser(USER_ID)).thenReturn(1L);
        when(userEntitlementMapper.selectAllByUser(eq(USER_ID), eq(0L), eq(20L)))
                .thenReturn(List.of(refunded));
        when(courseInternalClient.courses(anyList()))
                .thenReturn(
                        List.of(new CourseSnapshot(COURSE_ID, "Spring Boot 实战", "cover.png", 3)));
        when(learningProgressMapper.selectByUserCourses(eq(USER_ID), anyList()))
                .thenReturn(List.of(progress(11L, 1, 0, 1)));

        MyCourseView view = myCourseService.myCourses(USER_ID, 1, 20).getRecords().get(0);

        assertEquals(33, view.getProgressPercent());
        assertEquals(11L, view.getLastLessonId());
        assertTrue(view.isRefunded());
    }

    private void stubEntitlement() {
        stubEntitlement(3);
    }

    private void stubEntitlement(int lessonCount) {
        when(userEntitlementMapper.countAllByUser(USER_ID)).thenReturn(1L);
        when(userEntitlementMapper.selectAllByUser(eq(USER_ID), eq(0L), eq(20L)))
                .thenReturn(List.of(entitlement()));
        when(courseInternalClient.courses(anyList()))
                .thenReturn(
                        List.of(
                                new CourseSnapshot(
                                        COURSE_ID, "Spring Boot 实战", "cover.png", lessonCount)));
    }

    private UserEntitlement entitlement() {
        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUserId(USER_ID);
        entitlement.setCourseId(COURSE_ID);
        entitlement.setOrderId(11L);
        entitlement.setSource(1);
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        return entitlement;
    }

    private LearningProgress progress(long lessonId, int completed, int position, int maxPosition) {
        return progress(
                lessonId, completed, position, maxPosition, LocalDateTime.of(2026, 3, 1, 10, 0));
    }

    private LearningProgress progress(long courseId, long lessonId, int completed) {
        LearningProgress progress = progress(lessonId, completed, 0, 0);
        progress.setCourseId(courseId);
        return progress;
    }

    private LearningProgress progress(
            long lessonId, int completed, int position, int maxPosition, LocalDateTime updatedAt) {
        LearningProgress progress = new LearningProgress();
        progress.setUserId(USER_ID);
        progress.setCourseId(COURSE_ID);
        progress.setLessonId(lessonId);
        progress.setPositionSeconds(position);
        progress.setMaxPositionSeconds(maxPosition);
        progress.setCompleted(completed);
        progress.setUpdatedAt(updatedAt);
        return progress;
    }
}
