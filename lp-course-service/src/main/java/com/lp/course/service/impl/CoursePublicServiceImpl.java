package com.lp.course.service.impl;

import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.service.CourseInternalService;
import com.lp.course.service.CoursePublicService;
import org.springframework.stereotype.Service;

/**
 * Public course queries backed by the same projection as the internal contract.
 *
 * <p>Reusing the internal read keeps a single price conversion path, so the purchase page can never
 * disagree with the order service about the price of the same course.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class CoursePublicServiceImpl implements CoursePublicService {

    private final CourseInternalService courseInternalService;

    /**
     * Creates the service with the internal reader.
     *
     * @param courseInternalService shared course projection
     * @author Caspian
     * @since 0.1.0
     */
    public CoursePublicServiceImpl(CourseInternalService courseInternalService) {
        this.courseInternalService = courseInternalService;
    }

    /**
     * Loads one course and strips internal only fields.
     *
     * @param courseId course primary key
     * @return public projection
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PublicCourseView course(long courseId) {
        CourseInternalView course = courseInternalService.course(courseId);
        return new PublicCourseView(
                course.getCourseId(),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPrice(),
                course.getStatus(),
                course.getLessonCount());
    }
}
