-- lp_cms 课时内容清理登记表（V11）
-- 范围：登记被删除课时指向的存储对象，供后续清理任务异步删除；不改动 course_lesson 与 lesson_upload。
-- 触发口径：删除单个课时或整个章节时，在同一事务内按 lesson.video_url 插入登记；
--   video_url 为空或全空白（从未确认上传）的课时不登记，章节删除用一条 INSERT ... SELECT 批量登记。
-- 幂等键：uk_object_key 落在 object_key 上；同一对象重复或并发登记命中已有行，不产生第二条登记。
-- 状态口径：0 PENDING / 1 RUNNING / 2 DONE / 3 FAILED；attempt_count 记录清理尝试次数。
-- 索引：idx_status_next 支撑按状态 + 下次可处理时间取批。
-- 执行窗口：纯增量建表，Flyway 启动时执行，不锁业务表，可在低峰期滚动发布窗口执行。
-- 执行后校验：
--   DESC lesson_content_cleanup;             -- 期望 status/attempt_count 为 NOT NULL
--   SHOW INDEX FROM lesson_content_cleanup;  -- 期望 PRIMARY、uk_object_key、idx_status_next
-- 回滚：DROP TABLE lesson_content_cleanup;

CREATE TABLE IF NOT EXISTS lesson_content_cleanup (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id     BIGINT UNSIGNED NOT NULL COMMENT 'course.id',
    object_key    VARCHAR(500)    NOT NULL COMMENT '待清理对象键，取自被删课时的 video_url',
    status        TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待清理 / 1 清理中 / 2 已完成 / 3 失败',
    attempt_count TINYINT         NOT NULL DEFAULT 0 COMMENT '清理尝试次数',
    next_retry_at DATETIME(3)     NULL COMMENT '下次可处理时间，空表示立即可处理',
    last_error    VARCHAR(255)    NULL COMMENT '最近一次失败原因',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_object_key (object_key),
    KEY idx_status_next (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课时内容清理登记';
