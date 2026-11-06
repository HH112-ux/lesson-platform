package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.dto.TeacherChapterRequest;
import com.lp.course.dto.TeacherChapterView;
import com.lp.course.dto.TeacherLessonRequest;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.TeacherCourseMapper;
import java.math.BigDecimal;
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

        service.deleteChapter(1001L, "TEACHER", 9L, 71L);

        verify(teacherCourseMapper).deleteChapter(9L, 71L);
        verify(teacherCourseMapper).deleteLessonsByChapter(9L, 71L);
    }

    /**
     * A lesson without a positive duration is rejected.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsLessonWithoutDuration() {
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
                                                "Intro", "course/9/1.mp4", null, 0, 1, null)));

        assertEquals(40001, exception.getCode());
        verify(teacherCourseMapper, never()).insertLesson(any());
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
                1001L,
                "TEACHER",
                9L,
                71L,
                new TeacherLessonRequest("Intro", "course/9/1.mp4", 600, 1, 1, 1));

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
                                        new TeacherLessonRequest(
                                                "Intro", "course/9/1.mp4", 600, 0, 1, 1)));

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
        lesson.setIsFree(0);
        lesson.setSort(sort);
        lesson.setStatus(1);
        return lesson;
    }
}
