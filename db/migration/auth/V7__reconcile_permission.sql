-- lp_ums 支付对账权限码（V7）
-- 背景：对账接口（/api/v1/reconcile/**）从 #180 起就要求 admin:reconcile:view /
-- recheck / handle，但 V3 的角色菜单种子没有写入这些码，导致 ADMIN 与 OPS 调用时
-- 一律返回 40301。本迁移补齐既有三个码，并为 SPEC-TRADE-014 AC-3 的日结新增 close。
-- 授权口径（2026-11-10 裁决）：view / recheck / handle 授予 ADMIN 与 OPS；close 仅 ADMIN。
-- 运营后台本期没有对账页面，因此四项都按按钮（type=2）落库，不产生前端菜单入口。

INSERT INTO sys_menu (parent_id, name, path, permission_code, type, sort_order, status) VALUES
    (0, '对账查询', '', 'admin:reconcile:view', 2, 70, 1),
    (0, '对账重跑', '', 'admin:reconcile:recheck', 2, 71, 1),
    (0, '对账差异处置', '', 'admin:reconcile:handle', 2, 72, 1),
    (0, '日结', '', 'admin:reconcile:close', 2, 73, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), path = VALUES(path), type = VALUES(type),
                        sort_order = VALUES(sort_order), status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'ADMIN'
  AND m.permission_code IN (
        'admin:reconcile:view', 'admin:reconcile:recheck',
        'admin:reconcile:handle', 'admin:reconcile:close')
  AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
         CROSS JOIN sys_menu m
WHERE r.code = 'OPS'
  AND m.permission_code IN (
        'admin:reconcile:view', 'admin:reconcile:recheck', 'admin:reconcile:handle')
  AND m.deleted = 0;

-- 回滚：
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
--     WHERE m.permission_code LIKE 'admin:reconcile:%';
--   DELETE FROM sys_menu WHERE permission_code LIKE 'admin:reconcile:%';
