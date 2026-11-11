-- lp_cms 课时视频转码任务表（V10）
-- 范围：承载上传确认后的异步标准化转码任务；只新建本表，不改动 lesson_upload 与 course_lesson。
-- 触发口径：confirm 成功后按源对象键插入一条任务；同一源对象只允许一行，重复 confirm 直接命中
--   已有记录，READY 时不再转码。
-- 状态口径：0 PENDING / 1 RUNNING / 2 READY / 3 FAILED；attempt_count 记录已执行次数，最多 3 次。
-- 幂等键：uk_source_object 落在 source_object_key 上。对象键由 lessonId + 上传会话 id 派生，天然唯一；
--   source_size_bytes 与键一起构成「size + key」口径，同一对象不会产生第二条任务。
-- 产物：output_object_key 指向同桶 transcoded/ 前缀，源对象保持不变。
-- 执行窗口：纯增量建表，Flyway 启动时执行，不锁业务表，可在低峰期滚动发布窗口执行。
-- 执行后校验：
--   DESC lesson_video_transcode;      -- 期望 status/attempt_count 为 NOT NULL
--   SHOW INDEX FROM lesson_video_transcode;
--   -- 期望 PRIMARY、uk_source_object、idx_lesson_status、idx_status_created
-- 回滚：DROP TABLE lesson_video_transcode;

CREATE TABLE IF NOT EXISTS lesson_video_transcode (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id         BIGINT UNSIGNED NOT NULL COMMENT 'course_lesson.id',
    upload_id         BIGINT UNSIGNED NOT NULL COMMENT 'lesson_upload.id，确认时产生',
    source_object_key VARCHAR(255)    NOT NULL COMMENT '源对象键，幂等键',
    source_size_bytes BIGINT UNSIGNED NOT NULL COMMENT '源对象大小（字节）',
    output_object_key VARCHAR(255)    NULL COMMENT '转码产物对象键，READY 后写入',
    status            TINYINT         NOT NULL DEFAULT 0 COMMENT '0 PENDING / 1 RUNNING / 2 READY / 3 FAILED',
    attempt_count     TINYINT         NOT NULL DEFAULT 0 COMMENT '已执行次数，最多 3 次',
    failure_reason    VARCHAR(255)    NULL COMMENT '最近一次失败原因',
    started_at        DATETIME(3)     NULL COMMENT '首次开始执行时间',
    finished_at       DATETIME(3)     NULL COMMENT 'READY 或最终 FAILED 的时间',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_object (source_object_key),
    KEY idx_lesson_status (lesson_id, status),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课时视频转码任务';
