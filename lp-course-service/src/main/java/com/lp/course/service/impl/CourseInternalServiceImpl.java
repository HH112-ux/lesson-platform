package com.lp.course.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.service.CourseInternalService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Default {@link CourseInternalService} backed by {@link CourseMapper}.
 *
 * <p>Prices are converted from yuan to fen exactly; rounding is never applied because a fraction of
 * a fen would signal corrupt pricing data instead.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class CourseInternalServiceImpl implements CourseInternalService {

    private final CourseMapper courseMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param courseMapper mapper reading the {@code lp_cms} course tables
     * @author Caspian
     * @since 0.1.0
     */
    public CourseInternalServiceImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    /**
     * Loads one course or fails with {@code 40401}.
     *
     * @param courseId course primary key
     * @return course projection
     * @throws BizException when the course is absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseInternalView course(long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toView(course);
    }

    /**
     * Loads a batch of courses; unknown ids are silently omitted.
     *
     * @param courseIds course primary keys, may be empty
     * @return projections in database order
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<CourseInternalView> courses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectByIds(courseIds).stream().map(this::toView).toList();
    }

    /**
     * Lists the visible lessons of a course or fails with {@code 40401}.
     *
     * @param courseId course primary key
     * @return lesson outline
     * @throws BizException when the course is absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<LessonInternalView> lessons(long courseId) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return courseMapper.selectLessons(courseId).stream().map(this::toView).toList();
    }

    private CourseInternalView toView(Course course) {
        long lessonCount = course.getLessonCount() == null ? 0L : course.getLessonCount();
        return new CourseInternalView(
                course.getId(),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPrice().movePointRight(2).longValueExact(),
                course.getStatus(),
                course.getOwnerTeacherId(),
                lessonCount);
    }

    private LessonInternalView toView(CourseLesson lesson) {
        return new LessonInternalView(
                lesson.getId(),
                lesson.getChapterId(),
                lesson.getTitle(),
                lesson.getIsFree() != null && lesson.getIsFree() == 1,
                lesson.getSort() == null ? 0 : lesson.getSort());
    }
}
