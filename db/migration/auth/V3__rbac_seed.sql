-- lp_ums 最小 RBAC 闭环（V3）
-- V2 已建立 sys_role / sys_user_role，本迁移只补菜单与角色菜单关联，并写入角色、菜单种子。
-- 可重复执行：建表用 IF NOT EXISTS，种子用 ON DUPLICATE KEY UPDATE / INSERT IGNORE，重复执行不产生副作用。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 {表名}_id、时间字段 *_at、枚举 TINYINT、索引 idx_/uk_。

CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单 id，0 表示根',
    name            VARCHAR(32)     NOT NULL COMMENT '展示名',
    path            VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '前端路由，按钮为空',
    permission_code VARCHAR(64)     NULL COMMENT '权限标识，如 admin:course:list',
    type            TINYINT         NOT NULL DEFAULT 1 COMMENT '0 目录 / 1 菜单 / 2 按钮',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '同级排序',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_parent_id (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单与权限';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id    BIGINT UNSIGNED NOT NULL COMMENT 'sys_role.id',
    menu_id    BIGINT UNSIGNED NOT NULL COMMENT 'sys_menu.id',
    version    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    KEY idx_menu_id (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色菜单关联';

INSERT INTO sys_role (code, name, description, status) VALUES
    ('STUDENT', '学员', '购买并学习课程', 1),
    ('TEACHER', '教师', '自助建课并管理本人课程', 1),
    ('OPS', '运营', '平台运营与内容审核', 1),
    ('ADMIN', '管理员', '系统管理与全局配置', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description),
                        status = VALUES(status);

INSERT INTO sys_menu (parent_id, name, path, permission_code, type, sort_order, status) VALUES
    (0, '数据看板', '/admin/dashboard', 'admin:dashboard:view', 1, 10, 1),
    (0, '课程管理', '/admin/courses', 'admin:course:list', 1, 20, 1),
    (0, '用户管理', '/admin/users', 'admin:user:list', 1, 30, 1),
    (0, '订单管理', '/admin/orders', 'admin:order:list', 1, 40, 1),
    (0, '教师审核', '/admin/teachers', 'admin:teacher:audit', 1, 50, 1),
    (0, '教师工作台', '/teacher/courses', 'teacher:course:manage', 1, 60, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), type = VALUES(type),
                        sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO sys_menu (parent_id, name, path, permission_code, type, sort_order, status)
SELECT id, '审核课程', '', 'admin:course:review', 2, 21, 1
FROM sys_menu WHERE permission_code = 'admin:course:list'
ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id), type = VALUES(type),
                        sort_order = VALUES(sort_order), status = VALUES(status);

-- 管理员不进入教师工作台，教师菜单只分配给 TEACHER 角色。
DELETE FROM sys_role_menu
WHERE role_id = (SELECT id FROM sys_role WHERE code = 'ADMIN')
  AND menu_id = (SELECT id FROM sys_menu WHERE permission_code = 'teacher:course:manage');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'ADMIN' AND m.deleted = 0 AND m.permission_code <> 'teacher:course:manage';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'OPS' AND m.deleted = 0 AND m.permission_code IN (
        'admin:dashboard:view', 'admin:course:list', 'admin:course:review',
        'admin:user:list', 'admin:order:list', 'admin:teacher:audit');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'TEACHER' AND m.deleted = 0 AND m.permission_code = 'teacher:course:manage';
