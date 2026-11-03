-- lp_ums 微信小程序绑定表（V4）
-- 范围：小程序 code 登录所需的 openid 绑定关系（sys_user_wechat）。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 user_id、时间字段 *_at、唯一键 uk_openid。
-- session_key 仅供服务端使用，禁止写日志；本迁移可重复执行，重复执行不新增行。

CREATE TABLE IF NOT EXISTS sys_user_wechat (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT 'sys_user.id',
    openid      VARCHAR(128)    NOT NULL COMMENT '微信 openid',
    unionid     VARCHAR(128)    NULL COMMENT '微信 unionid，未返回时为空',
    session_key VARCHAR(255)    NULL COMMENT 'jscode2session 会话密钥，禁止落日志',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信小程序账号绑定';
