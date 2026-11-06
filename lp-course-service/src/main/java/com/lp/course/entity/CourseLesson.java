package com.lp.course.entity;

/**
 * Lesson row read from the {@code course_lesson} table.
 *
 * <p>Trade only needs the outline plus the trial flag, so video metadata is not projected.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseLesson {

    private Long id;

    private Long courseId;

    private Long chapterId;

    private String title;

    private Integer isFree;

    private Integer sort;

    private String videoUrl;

    private Integer videoDurationSec;

    private Integer status;

    private Integer courseStatus;

    /**
     * Returns the lesson id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the lesson id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the owning course id.
     *
     * @return {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getCourseId() {
        return courseId;
    }

    /**
     * Sets the owning course id.
     *
     * @param courseId {@code course.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    /**
     * Returns the chapter id.
     *
     * @return {@code course_chapter.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getChapterId() {
        return chapterId;
    }

    /**
     * Sets the chapter id.
     *
     * @param chapterId {@code course_chapter.id}
     * @author Caspian
     * @since 0.1.0
     */
    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    /**
     * Returns the lesson title.
     *
     * @return title text
     * @author Caspian
     * @since 0.1.0
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the lesson title.
     *
     * @param title title text
     * @author Caspian
     * @since 0.1.0
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the trial flag as stored in the database.
     *
     * @return 1 trial, 0 requires purchase
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getIsFree() {
        return isFree;
    }

    /**
     * Sets the trial flag.
     *
     * @param isFree 1 trial, 0 requires purchase
     * @author Caspian
     * @since 0.1.0
     */
    public void setIsFree(Integer isFree) {
        this.isFree = isFree;
    }

    /**
     * Returns the display order inside the chapter.
     *
     * @return sort value
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Sets the display order inside the chapter.
     *
     * @param sort sort value
     * @author Caspian
     * @since 0.1.0
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * Returns the MinIO object key of the lesson video.
     *
     * @return object key, never a public URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getVideoUrl() {
        return videoUrl;
    }

    /**
     * Sets the MinIO object key of the lesson video.
     *
     * @param videoUrl object key
     * @author Caspian
     * @since 0.1.0
     */
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    /**
     * Returns the video duration.
     *
     * @return duration in seconds, {@code 0} when unknown
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getVideoDurationSec() {
        return videoDurationSec;
    }

    /**
     * Sets the video duration.
     *
     * @param videoDurationSec duration in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setVideoDurationSec(Integer videoDurationSec) {
        this.videoDurationSec = videoDurationSec;
    }

    /**
     * Returns the visibility flag.
     *
     * @return 1 visible, 0 hidden
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the visibility flag.
     *
     * @param status 1 visible, 0 hidden
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the status of the owning course, projected only by the playback query.
     *
     * @return 0 draft, 1 pending review, 2 published, 3 offline; {@code null} when not selected
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getCourseStatus() {
        return courseStatus;
    }

    /**
     * Sets the status of the owning course.
     *
     * @param courseStatus 0 draft, 1 pending review, 2 published, 3 offline
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseStatus(Integer courseStatus) {
        this.courseStatus = courseStatus;
    }
}
