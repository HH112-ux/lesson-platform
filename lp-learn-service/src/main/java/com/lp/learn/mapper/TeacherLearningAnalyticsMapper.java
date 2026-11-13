package com.lp.learn.mapper;

import com.lp.learn.mapper.row.TeacherAnalyticsRows;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only aggregations backing the teacher learning dashboard.
 *
 * <p>Every statement is scoped by an explicit course id list. The owner check happens in the
 * course-service before this mapper is reached, so the learn-service never needs a teacher id and
 * cannot be asked for another teacher's courses by construction.
 *
 * <p>{@code watched_sec} is a lifetime summation per student and lesson; it is exposed as a
 * cumulative value only. The statements deliberately do not try to derive a windowed increment: no
 * daily snapshot exists, and inventing one would produce numbers that look precise but are not.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Mapper
public interface TeacherLearningAnalyticsMapper {

    /**
     * Returns the teacher wide aggregates over every requested course.
     *
     * @param courseIds owned course ids, must not be empty
     * @param startAt inclusive start of the natural day window
     * @param endAt exclusive end of the natural day window
     * @return one aggregate row
     * @author Caspian
     * @since 0.16.0
     */
    @Select(
            "<script>SELECT "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND status = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "THEN user_id END) AS studentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'p' THEN user_id END) "
                    + "AS learningStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'p' AND updated_at &gt;= #{startAt} "
                    + "AND updated_at &lt; #{endAt} THEN user_id END) AS activeStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND granted_at &gt;= #{startAt} "
                    + "AND granted_at &lt; #{endAt} THEN user_id END) AS newStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND status = 1 "
                    + "AND revoked_at &gt;= #{startAt} AND revoked_at &lt; #{endAt} "
                    + "THEN user_id END) AS revokedStudentCount, "
                    + "COALESCE(SUM(watched_sec), 0) AS cumulativeWatchedSeconds, "
                    + "COALESCE(SUM(completed = 1), 0) AS completedLessons, "
                    + "MAX(updated_at) AS lastLearnedAt "
                    + "FROM ("
                    + "SELECT 'e' AS src, user_id, course_id, status, expire_at, granted_at, "
                    + "revoked_at, NULL AS updated_at, 0 AS watched_sec, NULL AS completed "
                    + "FROM user_entitlement WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> "
                    + "UNION ALL "
                    + "SELECT 'p' AS src, user_id, course_id, NULL, NULL, NULL, NULL, "
                    + "updated_at, watched_sec, completed FROM learning_progress "
                    + "WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach>"
                    + ") t</script>")
    TeacherAnalyticsRows.OverviewRow selectOverview(
            @Param("courseIds") List<Long> courseIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /**
     * Returns the per-course aggregates of the requested courses.
     *
     * @param courseIds owned course ids, must not be empty
     * @param startAt inclusive start of the natural day window
     * @param endAt exclusive end of the natural day window
     * @return one row per course that has an entitlement or a progress row
     * @author Caspian
     * @since 0.16.0
     */
    @Select(
            "<script>SELECT course_id AS courseId, "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND status = 0 "
                    + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP(3)) "
                    + "THEN user_id END) AS studentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'p' THEN user_id END) "
                    + "AS learningStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'p' AND updated_at &gt;= #{startAt} "
                    + "AND updated_at &lt; #{endAt} THEN user_id END) AS activeStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND granted_at &gt;= #{startAt} "
                    + "AND granted_at &lt; #{endAt} THEN user_id END) AS newStudentCount, "
                    + "COUNT(DISTINCT CASE WHEN src = 'e' AND status = 1 "
                    + "AND revoked_at &gt;= #{startAt} AND revoked_at &lt; #{endAt} "
                    + "THEN user_id END) AS revokedStudentCount, "
                    + "COALESCE(SUM(watched_sec), 0) AS cumulativeWatchedSeconds, "
                    + "COALESCE(SUM(completed = 1), 0) AS completedLessons, "
                    + "MAX(updated_at) AS lastLearnedAt "
                    + "FROM ("
                    + "SELECT 'e' AS src, user_id, course_id, status, expire_at, granted_at, "
                    + "revoked_at, NULL AS updated_at, 0 AS watched_sec, NULL AS completed "
                    + "FROM user_entitlement WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> "
                    + "UNION ALL "
                    + "SELECT 'p' AS src, user_id, course_id, NULL, NULL, NULL, NULL, "
                    + "updated_at, watched_sec, completed FROM learning_progress "
                    + "WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach>"
                    + ") t GROUP BY course_id</script>")
    List<TeacherAnalyticsRows.CourseMetricRow> selectCourseMetrics(
            @Param("courseIds") List<Long> courseIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /**
     * Returns distinct active students per natural day inside the window.
     *
     * <p>A progress row carries only the latest update time, so a student who studies the same
     * lesson across several days appears on the last day only. Callers must treat the series as a
     * lower bound.
     *
     * @param courseIds owned course ids, must not be empty
     * @param startAt inclusive start of the natural day window
     * @param endAt exclusive end of the natural day window
     * @return one row per day that has activity, unordered
     * @author Caspian
     * @since 0.16.0
     */
    @Select(
            "<script>SELECT DATE(updated_at) AS statDate, COUNT(DISTINCT user_id) AS studentCount "
                    + "FROM learning_progress WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> "
                    + "AND updated_at &gt;= #{startAt} AND updated_at &lt; #{endAt} "
                    + "GROUP BY DATE(updated_at) ORDER BY statDate</script>")
    List<TeacherAnalyticsRows.DailyActiveRow> selectDailyActive(
            @Param("courseIds") List<Long> courseIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /**
     * Returns how many students reached each completed lesson count per course.
     *
     * <p>Students with zero completed lessons are absent; the caller derives them from the learning
     * student count. The compact shape keeps the cross-service payload small even for large
     * cohorts.
     *
     * @param courseIds owned course ids, must not be empty
     * @return one row per course and completed lesson count
     * @author Caspian
     * @since 0.16.0
     */
    @Select(
            "<script>SELECT course_id AS courseId, completed_count AS completedCount, "
                    + "COUNT(*) AS studentCount FROM ("
                    + "SELECT course_id, user_id, COUNT(*) AS completed_count "
                    + "FROM learning_progress WHERE deleted = 0 AND completed = 1 "
                    + "AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> "
                    + "GROUP BY course_id, user_id) t "
                    + "GROUP BY course_id, completed_count</script>")
    List<TeacherAnalyticsRows.HistogramRow> selectCompletedHistogram(
            @Param("courseIds") List<Long> courseIds);

    /**
     * Returns learner and completion counters of every reported lesson.
     *
     * @param courseIds owned course ids, must not be empty
     * @return one row per lesson that has progress, unordered
     * @author Caspian
     * @since 0.16.0
     */
    @Select(
            "<script>SELECT course_id AS courseId, lesson_id AS lessonId, "
                    + "COUNT(DISTINCT user_id) AS learners, "
                    + "COUNT(DISTINCT CASE WHEN completed = 1 THEN user_id END) "
                    + "AS completedStudents FROM learning_progress "
                    + "WHERE deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> GROUP BY course_id, lesson_id</script>")
    List<TeacherAnalyticsRows.LessonMetricRow> selectLessonMetrics(
            @Param("courseIds") List<Long> courseIds);
}
