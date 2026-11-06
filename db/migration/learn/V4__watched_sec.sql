-- lp_lms 学习进度：累计有效观看时长（V4）
-- 目的：完成态改由累计有效观看秒数判定（watched_sec >= 课时时长 × 90%），堵住"拖到片尾就算学完"。
-- 保留 max_position_seconds 仅用于续播；两者语义不同，不可互相替代。
-- 幂等：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，改为查询 information_schema 后动态执行，
--       重复执行只会命中 SELECT 1，不报错、不重复加列。

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_progress'
      AND column_name = 'watched_sec'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE learning_progress ADD COLUMN watched_sec INT NOT NULL DEFAULT 0 COMMENT ''累计有效观看秒数，仅服务端累加'' AFTER max_position_seconds',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回滚方式（仅限本地/开发环境，生产禁止破坏性回滚）：
-- 1) ALTER TABLE learning_progress DROP COLUMN watched_sec;
-- 生产回滚以代码 revert 为准，列保留（默认 0）不影响旧逻辑。
