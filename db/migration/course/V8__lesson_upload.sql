-- lp_cms 课时上传会话表（V8）
-- 范围：承载 L1-11 内容生产通路的预签名直传会话；只新建本表，不改动 course_lesson 既有结构。
-- 上传方式：服务端签发 30 分钟有效的预签名 PUT 凭证，客户端直传对象存储；上传完成后由服务端
--   statObject 确认对象存在与大小，本期不做转码，原文件直接作为播放源。
-- 状态口径：0 待上传 / 1 已确认 / 2 已过期 / 3 已中止。
-- pending_flag：由 status 派生的生成列，status = 0 时为 1，其余状态为 NULL；与 lesson_id 组成
--   唯一键 uk_lesson_pending，保证同一课时同时只有一条待完成的上传会话。
--   不采用布尔列的理由（与 trade V7 / auth V6 的 active_* 生成列一致）：
--   1) 生成列不会被漏写，应用层只改 status 即可自动维护唯一键口径，避免两个字段不一致；
--   2) MySQL 唯一索引允许多个 NULL 组合，已结束（1/2/3）的历史会话不占用唯一键。
-- 并发插入行为：两个请求同时为同一 lesson_id 创建会话时，第一条 INSERT 成功，第二条因
--   uk_lesson_pending 冲突抛 DuplicateKeyException；应用层捕获后重查胜者记录返回给客户端，
--   而不是把冲突暴露成 500。会话过期后需先把旧行 status 置 2，唯一键冲突才会解除。
-- 大小上限：expected_size_bytes ≤ 209715200（200 MiB），由接口层先行校验；本表的 CHECK 约束
--   是最后兜底，命中即拒绝写入，不产生可发布课时。
-- 执行窗口：纯增量建表，Flyway 启动时执行，不锁业务表，可在低峰期滚动发布窗口执行。
-- 执行后校验：
--   DESC lesson_upload;             -- 期望 pending_flag 为 STORED GENERATED，其余状态列为 NOT NULL
--   SHOW INDEX FROM lesson_upload;  -- 期望 PRIMARY、uk_lesson_pending、idx_status_expires、idx_lesson_created
-- 回滚：DROP TABLE lesson_upload;

CREATE TABLE IF NOT EXISTS lesson_upload (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id           BIGINT UNSIGNED NOT NULL COMMENT 'course.id',
    chapter_id          BIGINT UNSIGNED NOT NULL COMMENT 'course_chapter.id',
    lesson_id           BIGINT UNSIGNED NOT NULL COMMENT 'course_lesson.id',
    uploader_id         BIGINT UNSIGNED NOT NULL COMMENT '发起上传的教师 user id，确认时校验归属',
    object_key          VARCHAR(255)    NOT NULL COMMENT '对象存储键（非公网直链），服务端生成',
    content_type        VARCHAR(64)     NOT NULL DEFAULT 'video/mp4' COMMENT '声明的内容类型，本期固定 video/mp4',
    expected_size_bytes BIGINT UNSIGNED NOT NULL COMMENT '声明文件大小（字节），上限 209715200',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待上传 / 1 已确认 / 2 已过期 / 3 已中止',
    pending_flag        TINYINT         GENERATED ALWAYS AS (CASE WHEN status = 0 THEN 1 ELSE NULL END) STORED
        COMMENT '待上传标记：status=0 时为 1，其余为 NULL，仅用于 uk_lesson_pending',
    session_expires_at  DATETIME(3)     NOT NULL COMMENT '上传凭证过期时间（签发后 30 分钟）',
    confirmed_at        DATETIME(3)     NULL COMMENT 'statObject 确认时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_pending (lesson_id, pending_flag),
    KEY idx_status_expires (status, session_expires_at),
    KEY idx_lesson_created (lesson_id, created_at),
    CONSTRAINT chk_lesson_upload_size CHECK (expected_size_bytes > 0 AND expected_size_bytes <= 209715200)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课时上传会话';
