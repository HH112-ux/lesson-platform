-- lp_ums 账号与角色最小闭环（V2）
-- 范围：登录链路所需的三张表（sys_user / sys_role / sys_user_role）。
-- 菜单、教师档案、微信绑定与审计表随对应里程碑追加，本迁移不凭空新增业务表。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 {表名}_id、时间字段 *_at、索引 idx_/uk_。

CREATE TABLE sys_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)     NOT NULL COMMENT '登录名，字母数字 4-20',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt',
    nickname      VARCHAR(32)     NOT NULL COMMENT '展示名',
    avatar_url    VARCHAR(255)    NULL COMMENT '头像地址',
    email         VARCHAR(128)    NULL COMMENT '邮箱，可空且唯一',
    phone         VARCHAR(20)     NULL COMMENT '手机号，可空且唯一',
    gender        TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未知 / 1 男 / 2 女',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用 / 2 注销冷静期 / 3 已注销',
    last_login_at DATETIME(3)     NULL COMMENT '最近登录时间',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    KEY idx_status (status),
    KEY idx_nickname (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户账号';

CREATE TABLE sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(32)     NOT NULL COMMENT 'STUDENT / TEACHER / OPS / ADMIN',
    name        VARCHAR(32)     NOT NULL COMMENT '展示名',
    description VARCHAR(200)    NULL COMMENT '说明',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE TABLE sys_user_role (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT 'sys_user.id',
    role_id    BIGINT UNSIGNED NOT NULL COMMENT 'sys_role.id',
    version    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联';
