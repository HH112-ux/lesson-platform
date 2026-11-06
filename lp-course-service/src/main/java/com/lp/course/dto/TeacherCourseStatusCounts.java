package com.lp.course.dto;

/**
 * Number of courses the caller owns per status.
 *
 * <p>The counters ignore the status filter and pagination of the list request, so the teacher
 * console can render every tab badge from one response.
 *
 * @param draft number of draft courses
 * @param pending number of courses pending review
 * @param published number of published courses
 * @param offline number of offline courses
 * @author Caspian
 * @since 0.2.0
 */
public record TeacherCourseStatusCounts(int draft, int pending, int published, int offline) {

    /**
     * Returns an all-zero counter.
     *
     * @return counters with every status set to zero
     * @author Caspian
     * @since 0.2.0
     */
    public static TeacherCourseStatusCounts empty() {
        return new TeacherCourseStatusCounts(0, 0, 0, 0);
    }
}
