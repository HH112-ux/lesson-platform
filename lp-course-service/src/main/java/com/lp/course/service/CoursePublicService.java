package com.lp.course.service;

import com.lp.course.dto.CourseCategoryView;
import com.lp.course.dto.CoursePageView;
import com.lp.course.dto.CourseSyllabusView;
import com.lp.course.dto.PublicCourseView;
import java.util.List;

/**
 * Read-only course queries for the public web API.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface CoursePublicService {

    /**
     * Loads the sellable projection of one course.
     *
     * @param courseId course primary key
     * @param userId current user id, {@code null} when anonymous
     * @return public course projection, including the status for offline courses
     * @author Caspian
     * @since 0.1.0
     */
    PublicCourseView course(long courseId, Long userId);

    /**
     * Loads the chapter and lesson outline of one published course.
     *
     * @param courseId course primary key
     * @param userId current user id, {@code null} when anonymous
     * @return syllabus whose lessons carry the caller specific playable flag
     * @author Caspian
     * @since 0.1.0
     */
    CourseSyllabusView syllabus(long courseId, Long userId);

    /**
     * Lists one page of published courses.
     *
     * @param category category id or slug, {@code null} or blank for all categories
     * @param keyword title/subtitle fragment, {@code null} or blank for no filter
     * @param page one based page number
     * @param size page size, capped at 100
     * @param userId current user id, {@code null} when anonymous
     * @return published courses with the owned flag filled for a signed-in caller
     * @author Caspian
     * @since 0.1.0
     */
    CoursePageView list(String category, String keyword, int page, int size, Long userId);

    /**
     * Lists the categories shown by the storefront filter bar.
     *
     * @return visible categories in display order
     * @author Caspian
     * @since 0.1.0
     */
    List<CourseCategoryView> categories();
}
