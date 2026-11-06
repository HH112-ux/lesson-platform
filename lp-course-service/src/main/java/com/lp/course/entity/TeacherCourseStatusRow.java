package com.lp.course.entity;

/**
 * Aggregated course counters per status.
 *
 * <p>Mapped from a single grouped query so the teacher list stays one round trip.
 *
 * @author Caspian
 * @since 0.2.0
 */
public class TeacherCourseStatusRow {

    private Integer draft;

    private Integer pending;

    private Integer published;

    private Integer offline;

    /**
     * Returns the draft counter.
     *
     * @return draft courses
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getDraft() {
        return draft;
    }

    /**
     * Sets the draft counter.
     *
     * @param draft draft courses
     * @author Caspian
     * @since 0.2.0
     */
    public void setDraft(Integer draft) {
        this.draft = draft;
    }

    /**
     * Returns the pending-review counter.
     *
     * @return courses pending review
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getPending() {
        return pending;
    }

    /**
     * Sets the pending-review counter.
     *
     * @param pending courses pending review
     * @author Caspian
     * @since 0.2.0
     */
    public void setPending(Integer pending) {
        this.pending = pending;
    }

    /**
     * Returns the published counter.
     *
     * @return published courses
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getPublished() {
        return published;
    }

    /**
     * Sets the published counter.
     *
     * @param published published courses
     * @author Caspian
     * @since 0.2.0
     */
    public void setPublished(Integer published) {
        this.published = published;
    }

    /**
     * Returns the offline counter.
     *
     * @return offline courses
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getOffline() {
        return offline;
    }

    /**
     * Sets the offline counter.
     *
     * @param offline offline courses
     * @author Caspian
     * @since 0.2.0
     */
    public void setOffline(Integer offline) {
        this.offline = offline;
    }
}
