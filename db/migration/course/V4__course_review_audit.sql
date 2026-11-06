-- lp_cms 课程审核审计流水表（V4）
-- 范围：每次课程审核决策落一条不可变流水，供运营追溯「谁在何时以什么理由通过/驳回」。
-- 与 course.reviewed_by / reviewed_at 并存：课程行只保留最近一次结果，本表保留完整历史。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 {表}_id、时间字段 *_at、索引 idx_。
-- 本迁移可重复执行（CREATE TABLE IF NOT EXISTS），重复执行不新增表、不报错。
-- 回滚：DROP TABLE IF EXISTS course_review_audit;

CREATE TABLE IF NOT EXISTS course_review_audit (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id   BIGINT UNSIGNED NOT NULL COMMENT 'course.id',
    action      TINYINT         NOT NULL COMMENT '1 通过 / 2 驳回',
    from_status TINYINT         NOT NULL COMMENT '决策前课程状态，固定为 1 待审核',
    to_status   TINYINT         NOT NULL COMMENT '决策后课程状态：2 已上架 / 0 草稿',
    reviewer_id BIGINT UNSIGNED NOT NULL COMMENT '审核人用户 id',
    remark      VARCHAR(300)    NULL COMMENT '审核意见，驳回时必填',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '决策时间',
    PRIMARY KEY (id),
    KEY idx_course_created (course_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程审核审计流水';
