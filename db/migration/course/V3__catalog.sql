-- lp_cms 课程目录表（V3）
-- 范围：课程分类与讲师档案，供公开课程列表/详情展示讲师与按分类筛选使用。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 {表}_id、时间字段 *_at、唯一键 uk_、索引 idx_。
-- 本迁移可重复执行（CREATE TABLE IF NOT EXISTS），重复执行不新增表、不报错。

CREATE TABLE IF NOT EXISTS course_category (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级分类，0 表示一级分类',
    name       VARCHAR(60)     NOT NULL COMMENT '分类名称',
    slug       VARCHAR(60)     NOT NULL COMMENT '英文标识，用于筛选参数',
    sort       INT             NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 隐藏',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_slug (slug),
    KEY idx_parent_sort (parent_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程分类';

CREATE TABLE IF NOT EXISTS teacher_profile (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT UNSIGNED NULL COMMENT 'auth 用户 id，本地种子可为空',
    display_name VARCHAR(60)     NOT NULL COMMENT '展示名称',
    avatar_url   VARCHAR(255)    NULL COMMENT '头像地址',
    bio          VARCHAR(500)    NULL COMMENT '简介',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 停用',
    deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '讲师档案';
