package com.lp.learn.mapper.row;

import java.time.LocalDateTime;

/**
 * Mutable rows returned by the teacher analytics aggregation queries.
 *
 * <p>Fields are public on purpose: MyBatis fills them directly and the rows never leave the
 * learn-service, so no getters or setters are needed.
 *
 * @author Caspian
 * @since 0.16.0
 */
public final class TeacherAnalyticsRows {

    private TeacherAnalyticsRows() {}

    /** Teacher wide aggregates over every requested course. */
    public static class OverviewRow {

        /** Distinct students holding an active, unexpired entitlement. */
        public long studentCount;

        /** Distinct students with at least one progress row. */
        public long learningStudentCount;

        /** Distinct students whose progress was updated inside the window. */
        public long activeStudentCount;

        /** Distinct students whose entitlement was granted inside the window. */
        public long newStudentCount;

        /** Distinct students whose entitlement was revoked inside the window. */
        public long revokedStudentCount;

        /** Sum of the cumulative effective watch seconds. */
        public long cumulativeWatchedSeconds;

        /** Completed lesson rows across every course. */
        public long completedLessons;

        /** Latest progress update time, {@code null} when nothing was reported. */
        public LocalDateTime lastLearnedAt;
    }

    /** Per-course aggregates of the same metrics as {@link OverviewRow}. */
    public static class CourseMetricRow {

        /** Course id. */
        public long courseId;

        /** Distinct students holding an active, unexpired entitlement. */
        public long studentCount;

        /** Distinct students with at least one progress row. */
        public long learningStudentCount;

        /** Distinct students whose progress was updated inside the window. */
        public long activeStudentCount;

        /** Distinct students whose entitlement was granted inside the window. */
        public long newStudentCount;

        /** Distinct students whose entitlement was revoked inside the window. */
        public long revokedStudentCount;

        /** Sum of the cumulative effective watch seconds. */
        public long cumulativeWatchedSeconds;

        /** Completed lesson rows of this course. */
        public long completedLessons;

        /** Latest progress update time, {@code null} when nothing was reported. */
        public LocalDateTime lastLearnedAt;
    }

    /** One point of the daily active student series. */
    public static class DailyActiveRow {

        /** Natural day of the last progress update. */
        public java.time.LocalDate statDate;

        /** Distinct active students on that day. */
        public long studentCount;
    }

    /** One bucket of the completed lesson histogram of a course. */
    public static class HistogramRow {

        /** Course id. */
        public long courseId;

        /** Completed lesson count reached by the students of this bucket. */
        public int completedCount;

        /** Number of students in this bucket. */
        public long studentCount;
    }

    /** Completion counters of one lesson. */
    public static class LessonMetricRow {

        /** Course id the lesson belongs to. */
        public long courseId;

        /** Lesson id. */
        public long lessonId;

        /** Distinct students that reported progress on this lesson. */
        public long learners;

        /** Distinct students that completed this lesson. */
        public long completedStudents;
    }
}
