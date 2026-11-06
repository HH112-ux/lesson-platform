package com.lp.course.mapper;

import com.lp.course.entity.CourseReviewAudit;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Appends and reads course review audit entries.
 *
 * <p>The table is append-only: no update or delete statement is exposed on purpose.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface CourseReviewAuditMapper {

    /**
     * Appends one review decision.
     *
     * @param audit audit row to insert, filled with the generated id
     * @return affected rows, always 1 when the insert succeeds
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO course_review_audit "
                    + "(course_id, action, from_status, to_status, reviewer_id, remark, created_at) "
                    + "VALUES (#{courseId}, #{action}, #{fromStatus}, #{toStatus}, #{reviewerId}, "
                    + "#{remark}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CourseReviewAudit audit);

    /**
     * Lists the audit history of one course, newest first.
     *
     * @param courseId course primary key
     * @return audit rows, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, action, from_status AS fromStatus, "
                    + "to_status AS toStatus, reviewer_id AS reviewerId, remark, "
                    + "created_at AS createdAt FROM course_review_audit "
                    + "WHERE course_id = #{courseId} ORDER BY created_at DESC, id DESC")
    List<CourseReviewAudit> selectByCourseId(@Param("courseId") long courseId);
}
