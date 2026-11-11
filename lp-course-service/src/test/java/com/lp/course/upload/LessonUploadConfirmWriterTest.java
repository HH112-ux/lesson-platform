package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.LessonUpload;
import com.lp.course.mapper.LessonContentCleanupMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonUploadConfirmWriterTest {

    private static final String OLD_KEY = "lesson-videos/77/401.mp4";
    private static final String NEW_KEY = "lesson-videos/77/502.mp4";
    private static final LessonVideoMetadata METADATA =
            new LessonVideoMetadata("isom", 90.4, "h264", 1280, 720, "aac");

    @Mock private TeacherCourseMapper teacherCourseMapper;
    @Mock private LessonUploadMapper lessonUploadMapper;
    @Mock private LessonContentCleanupMapper contentCleanupMapper;

    private LessonUploadConfirmWriter writer;

    @BeforeEach
    void setUp() {
        writer =
                new LessonUploadConfirmWriter(
                        teacherCourseMapper, lessonUploadMapper, contentCleanupMapper);
    }

    @Test
    void firstUploadRegistersNothing() {
        givenLesson("");
        givenClaim(1);

        assertEquals(1, writer.confirm(session(), METADATA, 1024L, "sha256-value"));

        verify(contentCleanupMapper, never()).insertObjectKeyCleanup(anyLong(), anyString());
    }

    @Test
    void overwriteRegistersTheReplacedKey() {
        givenLesson(OLD_KEY);
        givenClaim(1);

        assertEquals(1, writer.confirm(session(), METADATA, 1024L, "sha256-value"));

        verify(contentCleanupMapper).insertObjectKeyCleanup(9L, OLD_KEY);
        verify(contentCleanupMapper, never()).insertObjectKeyCleanup(anyLong(), eq(NEW_KEY));
    }

    @Test
    void repeatedConfirmationRegistersNothing() {
        givenLesson(OLD_KEY);
        givenClaim(0);

        assertEquals(0, writer.confirm(session(), METADATA, 1024L, "sha256-value"));

        verify(contentCleanupMapper, never()).insertObjectKeyCleanup(anyLong(), anyString());
    }

    @Test
    void blankPreviousKeyRegistersNothing() {
        givenLesson("   ");
        givenClaim(1);

        writer.confirm(session(), METADATA, 1024L, "sha256-value");

        verify(contentCleanupMapper, never()).insertObjectKeyCleanup(anyLong(), anyString());
    }

    @Test
    void missingLessonRegistersNothing() {
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(null);
        givenClaim(0);

        assertEquals(0, writer.confirm(session(), METADATA, 1024L, "sha256-value"));

        verify(contentCleanupMapper, never()).insertObjectKeyCleanup(anyLong(), anyString());
    }

    private void givenLesson(String videoUrl) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(77L);
        lesson.setVideoUrl(videoUrl);
        when(teacherCourseMapper.selectLesson(9L, 3L, 77L)).thenReturn(lesson);
    }

    private void givenClaim(int claimed) {
        when(lessonUploadMapper.confirm(
                        anyLong(),
                        any(),
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyInt(),
                        anyLong(),
                        anyString()))
                .thenReturn(claimed);
    }

    private LessonUpload session() {
        LessonUpload session = new LessonUpload();
        session.setId(502L);
        session.setCourseId(9L);
        session.setChapterId(3L);
        session.setLessonId(77L);
        session.setObjectKey(NEW_KEY);
        return session;
    }
}
