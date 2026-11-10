-- lp_ums 账号注销申请（V6）
-- 范围：SPEC-PLAT-004 注销冷静期所需的申请记录表（user_account_cancellation）。
-- 冷静期时长在申请时计算并落库到 effective_at，后续配置变更不改写已落库值。
-- 生成列 active_user_id 仅在进行中（status=0）时等于 user_id，配合唯一键保证同一用户只有一条进行中申请；
-- 撤销或执行后生成列变为 NULL，允许用户重新申请。
-- 本迁移可重复执行，重复执行不新增行，也不修改已有记录。

CREATE TABLE IF NOT EXISTS user_account_cancellation (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT 'sys_user.id',
    status         TINYINT         NOT NULL DEFAULT 0 COMMENT '0 冷静期 / 1 已撤销 / 2 已执行',
    requested_at   DATETIME(3)     NOT NULL COMMENT '申请时间',
    effective_at   DATETIME(3)     NOT NULL COMMENT '注销生效时间，申请时按 7×24 小时计算后固定',
    withdrawn_at   DATETIME(3)     NULL COMMENT '撤销时间',
    executed_at    DATETIME(3)     NULL COMMENT '执行时间',
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 0 THEN user_id ELSE NULL END) STORED COMMENT '进行中申请的用户，仅 status=0 时非空',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_active_user (active_user_id),
    KEY idx_user_requested (user_id, requested_at),
    KEY idx_status_effective (status, effective_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '账号注销申请';

-- 回滚：DROP TABLE IF EXISTS user_account_cancellation;
-- 该表只保存注销申请与时间点，回滚不涉及 sys_user、订单、支付、退款与权益数据。
