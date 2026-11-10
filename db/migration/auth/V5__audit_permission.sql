-- lp_ums 审计日志查询权限（V5）
-- 新增菜单节点 admin:audit:list，默认只授予 ADMIN 角色；运营角色默认不可见，
-- 需要时由管理员在后台单独授权，避免普通运营横向查看他人的审核留痕。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id = (SELECT id FROM sys_menu WHERE permission_code = 'admin:audit:list');
--   DELETE FROM sys_menu WHERE permission_code = 'admin:audit:list';

INSERT INTO sys_menu (parent_id, name, path, permission_code, type, sort_order, status)
VALUES (0, '审计日志', '/admin/audit-logs', 'admin:audit:list', 1, 55, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), type = VALUES(type),
                        sort_order = VALUES(sort_order), status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'ADMIN'
  AND m.permission_code = 'admin:audit:list'
  AND m.deleted = 0;
