package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.client.LearnEntitlementClient;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CoursePageView;
import com.lp.course.dto.CourseSyllabusView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.dto.PublicLessonView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseCategory;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.CourseCategoryMapper;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.service.CourseInternalService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the public course projection and catalog page.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class CoursePublicServiceImplTest {

    @Mock private CourseInternalService courseInternalService;

    @Mock private CourseMapper courseMapper;

    @Mock private CourseCategoryMapper courseCategoryMapper;

    @Mock private LearnEntitlementClient learnEntitlementClient;

    private CoursePublicServiceImpl coursePublicService;

    /**
     * Builds the service under test with mocked readers.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        coursePublicService =
                new CoursePublicServiceImpl(
                        courseInternalService,
                        courseMapper,
                        courseCategoryMapper,
                        learnEntitlementClient);
    }

    /**
     * A published course exposes only buyer visible fields with the price in fen.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void mapsPublishedCourse() {
        when(courseInternalService.course(7L))
                .thenReturn(
                        new CourseInternalView(
                                7L, "Course 7", "cover-7", 9900L, 2, 55L, 4L, "Teacher A"));

        PublicCourseView view = coursePublicService.course(7L, null);

        assertEquals(7L, view.getCourseId());
        assertEquals("Course 7", view.getTitle());
        assertEquals("cover-7", view.getCoverUrl());
        assertEquals(9900L, view.getPrice());
        assertEquals(2, view.getStatus());
        assertEquals(4L, view.getLessonCount());
        assertEquals("Teacher A", view.getInstructorName());
        assertFalse(view.isOwned());
        verifyNoInteractions(learnEntitlementClient);
    }

    /**
     * An offline course looks missing to an anonymous caller.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void hidesOfflineCourseFromAnonymousCaller() {
        when(courseInternalService.course(8L)).thenReturn(offlineCourse());

        BizException exception =
                assertThrows(BizException.class, () -> coursePublicService.course(8L, null));

        assertEquals(40401, exception.getCode());
        verify(learnEntitlementClient, never()).ownedCourseIds(anyLong(), anyList());
    }

    /**
     * An offline course looks missing to a signed-in caller that does not own it.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void hidesOfflineCourseFromNonOwner() {
        when(courseInternalService.course(8L)).thenReturn(offlineCourse());
        when(learnEntitlementClient.ownedCourseIds(1001L, List.of(8L))).thenReturn(Set.of());

        BizException exception =
                assertThrows(BizException.class, () -> coursePublicService.course(8L, 1001L));

        assertEquals(40401, exception.getCode());
    }

    /**
     * An owner can still open the detail and the outline of an offline course.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void keepsOfflineCourseReadableForOwner() {
        when(courseInternalService.course(8L)).thenReturn(offlineCourse());
        when(learnEntitlementClient.ownedCourseIds(1001L, List.of(8L))).thenReturn(Set.of(8L));
        when(courseMapper.selectChapters(8L)).thenReturn(List.of(chapter(10L, "Basics", 1)));
        when(courseMapper.selectLessons(8L))
                .thenReturn(List.of(lesson(100L, 10L, "Intro", 1, 300, 1)));

        PublicCourseView view = coursePublicService.course(8L, 1001L);
        CourseSyllabusView syllabus = coursePublicService.syllabus(8L, 1001L);

        assertEquals(3, view.getStatus());
        assertTrue(view.isOwned());
        assertTrue(syllabus.isOwned());
        assertEquals(1, syllabus.getChapters().size());
    }

    /**
     * A missing course keeps the documented {@code 40401} propagation.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void propagatesNotFound() {
        when(courseInternalService.course(99L)).thenThrow(new BizException(40401, "not found"));

        BizException exception =
                assertThrows(BizException.class, () -> coursePublicService.course(99L, null));

        assertEquals(40401, exception.getCode());
    }

    /**
     * A signed-in caller sees the owned flag filled from the learn-service.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void marksOwnedCoursesForSignedInCaller() {
        when(courseMapper.countPublished(null, null)).thenReturn(2L);
        when(courseMapper.selectPublishedPage(null, null, 0L, 20L))
                .thenReturn(List.of(course(1L, "Owned"), course(2L, "New")));
        when(learnEntitlementClient.ownedCourseIds(anyLong(), anyList())).thenReturn(Set.of(1L));

        CoursePageView page = coursePublicService.list(null, null, 1, 20, 1001L);

        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().get(0).isOwned());
        assertFalse(page.getItems().get(1).isOwned());
        assertEquals(2L, page.getTotal());
    }

    /**
     * An anonymous caller must not trigger an ownership lookup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void anonymousListSkipsOwnershipLookup() {
        when(courseMapper.countPublished(null, null)).thenReturn(1L);
        when(courseMapper.selectPublishedPage(null, null, 0L, 20L))
                .thenReturn(List.of(course(1L, "Course")));

        CoursePageView page = coursePublicService.list(null, null, 1, 20, null);

        assertEquals(1, page.getItems().size());
        assertFalse(page.getItems().get(0).isOwned());
        verify(learnEntitlementClient, never()).ownedCourseIds(anyLong(), anyList());
    }

    /**
     * The slug filter resolves to a category id; an unknown slug yields an empty page.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void resolvesCategorySlugAndRejectsUnknown() {
        CourseCategory category = new CourseCategory();
        category.setId(3L);
        category.setName("Design");
        category.setSlug("design");
        when(courseCategoryMapper.selectVisibleBySlug("design")).thenReturn(category);
        when(courseCategoryMapper.selectVisibleBySlug("missing")).thenReturn(null);
        when(courseMapper.countPublished(eq(3L), any())).thenReturn(1L);
        when(courseMapper.selectPublishedPage(eq(3L), any(), eq(0L), eq(10L)))
                .thenReturn(List.of(course(1L, "Course")));

        CoursePageView page = coursePublicService.list("design", "图", 1, 10, null);
        CoursePageView missing = coursePublicService.list("missing", null, 1, 10, null);

        assertEquals(1, page.getItems().size());
        assertEquals(0L, missing.getTotal());
    }

    /**
     * An anonymous caller sees the whole outline but only trial lessons are playable.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void syllabusMarksOnlyTrialLessonsPlayableForAnonymous() {
        when(courseInternalService.course(7L))
                .thenReturn(
                        new CourseInternalView(
                                7L, "Course 7", "cover-7", 9900L, 2, 55L, 4L, "Teacher A"));
        when(courseMapper.selectChapters(7L))
                .thenReturn(List.of(chapter(10L, "Basics", 1), chapter(11L, "Advanced", 2)));
        when(courseMapper.selectLessons(7L))
                .thenReturn(
                        List.of(
                                lesson(100L, 10L, "Intro", 1, 300, 1),
                                lesson(101L, 10L, "Setup", 0, 600, 2),
                                lesson(102L, 11L, "Deep dive", 0, 900, 1)));

        CourseSyllabusView syllabus = coursePublicService.syllabus(7L, null);

        assertEquals(2, syllabus.getChapters().size());
        assertFalse(syllabus.isOwned());
        PublicLessonView trial = syllabus.getChapters().get(0).getLessons().get(0);
        PublicLessonView paid = syllabus.getChapters().get(0).getLessons().get(1);
        assertTrue(trial.isFree());
        assertTrue(trial.isPlayable());
        assertFalse(paid.isPlayable());
        assertEquals(600, paid.getDurationSec());
        verify(learnEntitlementClient, never()).ownedCourseIds(anyLong(), anyList());
    }

    /**
     * A buyer sees every lesson as playable.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void syllabusMarksEverythingPlayableForOwner() {
        when(courseInternalService.course(7L))
                .thenReturn(
                        new CourseInternalView(
                                7L, "Course 7", "cover-7", 9900L, 2, 55L, 4L, "Teacher A"));
        when(learnEntitlementClient.ownedCourseIds(1001L, List.of(7L))).thenReturn(Set.of(7L));
        when(courseMapper.selectChapters(7L)).thenReturn(List.of(chapter(10L, "Basics", 1)));
        when(courseMapper.selectLessons(7L))
                .thenReturn(List.of(lesson(100L, 10L, "Intro", 1, 300, 1)));

        CourseSyllabusView syllabus = coursePublicService.syllabus(7L, 1001L);

        assertTrue(syllabus.isOwned());
        assertTrue(syllabus.getChapters().get(0).getLessons().get(0).isPlayable());
    }

    /**
     * Draft and pending-review courses stay invisible on both public reads.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void hidesUnpublishedCourses() {
        CourseInternalView draft =
                new CourseInternalView(9L, "Draft", "cover-9", 0L, 0, 57L, 2L, "Teacher C");
        when(courseInternalService.course(9L)).thenReturn(draft);

        BizException detail =
                assertThrows(BizException.class, () -> coursePublicService.course(9L, null));
        BizException syllabus =
                assertThrows(BizException.class, () -> coursePublicService.syllabus(9L, null));

        assertEquals(40401, detail.getCode());
        assertEquals(40401, syllabus.getCode());
        verifyNoInteractions(courseMapper);
    }

    /**
     * Builds the offline course projection shared by the visibility tests.
     *
     * @return course with {@code status=3}
     * @author Caspian
     * @since 0.1.0
     */
    private CourseInternalView offlineCourse() {
        return new CourseInternalView(8L, "Course 8", "cover-8", 19900L, 3, 56L, 6L, "Teacher B");
    }

    /**
     * Builds a chapter row.
     *
     * @param id chapter id
     * @param title chapter title
     * @param sort display order
     * @return row
     * @author Caspian
     * @since 0.1.0
     */
    private CourseChapter chapter(long id, String title, int sort) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(7L);
        chapter.setTitle(title);
        chapter.setSort(sort);
        return chapter;
    }

    /**
     * Builds a lesson row.
     *
     * @param id lesson id
     * @param chapterId owning chapter id
     * @param title lesson title
     * @param free 1 for a trial lesson
     * @param durationSec duration in seconds
     * @param sort display order
     * @return row
     * @author Caspian
     * @since 0.1.0
     */
    private CourseLesson lesson(
            long id, long chapterId, String title, int free, int durationSec, int sort) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(id);
        lesson.setCourseId(7L);
        lesson.setChapterId(chapterId);
        lesson.setTitle(title);
        lesson.setIsFree(free);
        lesson.setVideoDurationSec(durationSec);
        lesson.setSort(sort);
        return lesson;
    }

    /**
     * Builds a catalog row with a fen price of 99.
     *
     * @param id course id
     * @param title title
     * @return row
     * @author Caspian
     * @since 0.1.0
     */
    private Course course(long id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setCoverUrl("cover-" + id);
        course.setPrice(BigDecimal.valueOf(99));
        course.setStatus(2);
        course.setInstructorName("Teacher");
        course.setLessonCount(3L);
        return course;
    }
}
