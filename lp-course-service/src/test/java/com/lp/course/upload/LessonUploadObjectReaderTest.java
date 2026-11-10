package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import com.lp.course.config.CourseUploadProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the object reads of the lesson upload storage.
 *
 * @author Caspian
 * @since 0.8.0
 */
class LessonUploadObjectReaderTest {

    private static final CourseUploadProperties BLANK =
            new CourseUploadProperties(
                    Duration.ofMinutes(30), 209_715_200L, "", "", "", "", "lp-media");

    @Test
    void rejectsReadsWhenCredentialsAreMissing() {
        LessonUploadObjectReader reader = new LessonUploadObjectReader(BLANK);

        assertEquals(50206, assertThrows(BizException.class, () -> reader.statSize("a")).getCode());
        assertEquals(50206, assertThrows(BizException.class, () -> reader.download("a")).getCode());
    }
}
