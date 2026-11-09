package com.lp.learn.dto;

/**
 * Course progress exposed to trusted internal callers for refund admission.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseProgressSummaryView {

    private final int completedLessons;

    private final int lessonCount;

    private final int progressPercent;

    /**
     * Creates the summary.
     *
     * @param completedLessons completed lesson count
     * @param lessonCount live lesson count of the course
     * @param progressPercent completed share in percent, rounded half up and capped at 100
     * @author Caspian
     * @since 0.1.0
     */
    public CourseProgressSummaryView(int completedLessons, int lessonCount, int progressPercent) {
        this.completedLessons = completedLessons;
        this.lessonCount = lessonCount;
        this.progressPercent = progressPercent;
    }

    /**
     * Returns the completed lesson count.
     *
     * @return completed lessons
     * @author Caspian
     * @since 0.1.0
     */
    public int getCompletedLessons() {
        return completedLessons;
    }

    /**
     * Returns the live lesson count.
     *
     * @return lesson count
     * @author Caspian
     * @since 0.1.0
     */
    public int getLessonCount() {
        return lessonCount;
    }

    /**
     * Returns the completed share.
     *
     * @return progress percent from 0 to 100
     * @author Caspian
     * @since 0.1.0
     */
    public int getProgressPercent() {
        return progressPercent;
    }
}
