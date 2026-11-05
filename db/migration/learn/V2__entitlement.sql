-- lp_lms 学习域权益表（V2）
-- 范围：课程权益 user_entitlement。字段按 engineering-plan 4.3.4；命名按 AGENTS.md 11.3。
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行不新增行、不清空数据。
-- 唯一 uk_user_course(user_id, course_id) 是重复发放的最终兜底：同一用户同一课程只允许一条。
-- 权益写入来自 trade-service 的事务内 outbox，learn-service 侧按唯一索引吸收重复投递。

CREATE TABLE IF NOT EXISTS user_entitlement (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '学员',
    course_id  BIGINT UNSIGNED NOT NULL COMMENT '课程',
    order_id   BIGINT UNSIGNED NULL COMMENT 'trade_order.id；运营发放或兑换为空',
    source     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 购买 / 2 运营发放 / 3 兑换',
    granted_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发放时间',
    expire_at  DATETIME(3)     NULL COMMENT '失效时间，空=永久',
    version    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_course (user_id, course_id),
    KEY idx_course (course_id),
    KEY idx_user_granted (user_id, granted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程权益';

-- 回滚方式（仅限本地/开发环境，生产禁止破坏性回滚）：
-- 1) DROP TABLE user_entitlement;
-- 生产回滚以代码 revert 为准，表保留并由后续迁移兼容。
