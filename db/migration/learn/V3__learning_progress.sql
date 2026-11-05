-- lp_lms 学习进度表（V3）
-- 范围：按课时记录断点与历史最大播放位置；完成态由服务端按课时时长 90% 阈值重算。
-- 幂等：CREATE TABLE IF NOT EXISTS；唯一 uk_user_lesson(user_id, lesson_id) 保证一个用户一个课时一条。

CREATE TABLE IF NOT EXISTS learning_progress (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id              BIGINT UNSIGNED NOT NULL COMMENT '学员',
    course_id            BIGINT UNSIGNED NOT NULL COMMENT '课程（冗余，便于按课程查询）',
    lesson_id            BIGINT UNSIGNED NOT NULL COMMENT '课时',
    position_seconds     INT             NOT NULL DEFAULT 0 COMMENT '最近播放位置（秒），允许回退',
    max_position_seconds INT             NOT NULL DEFAULT 0 COMMENT '历史最大播放位置（秒），只增不减',
    completed            TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未完成 / 1 完成，仅服务端重算',
    version              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted              TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_lesson (user_id, lesson_id),
    KEY idx_user_course (user_id, course_id),
    KEY idx_course_lesson (course_id, lesson_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学习进度';

-- 回滚方式（仅限本地/开发环境，生产禁止破坏性回滚）：
-- 1) DROP TABLE learning_progress;
-- 生产回滚以代码 revert 为准，表保留并由后续迁移兼容。
