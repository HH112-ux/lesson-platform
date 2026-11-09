-- lp_oms 退款审核审计投递箱（V10）
-- 背景：统一审计表 admin_audit_log 位于 lp_cms，而退款状态在 lp_oms，无法跨库同事务写入。
--   退款审核事务内同时写入本表一行投递意图，保证“状态改了必有审计意图”；
--   trade-service 的中继按至少一次投递到 course-service 的 /internal/v1/audit-logs，
--   目标侧以 dedupe_key 唯一键去重，重复投递不会产生重复审计行。
-- 状态：0 待投递 / 1 已投递 / 2 已放弃（超过重试上限，需要人工对账）。
-- 动作类型：0 课程通过 / 1 课程驳回 / 2 退款通过 / 3 退款驳回 / 4 教师通过 / 5 教师驳回。
-- 对象类型：0 课程 / 1 退款 / 2 教师。操作人角色：0 运营 / 1 管理员。
-- 回滚：DROP TABLE audit_outbox;

CREATE TABLE IF NOT EXISTS audit_outbox (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dedupe_key    VARCHAR(128)    NOT NULL COMMENT '投递幂等键：refund-review:{refundId}:{毫秒时间戳}',
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '操作人 user id',
    operator_role TINYINT         NOT NULL COMMENT '操作人角色：0 运营 / 1 管理员',
    action_type   TINYINT         NOT NULL COMMENT '动作类型：2 退款通过 / 3 退款驳回',
    object_type   TINYINT         NOT NULL COMMENT '对象类型：1 退款',
    object_id     BIGINT UNSIGNED NOT NULL COMMENT '退款单主键',
    from_status   TINYINT         NOT NULL COMMENT '变更前状态：0 待审核',
    to_status     TINYINT         NOT NULL COMMENT '变更后状态：1 审核通过 / 3 驳回',
    remark        VARCHAR(300)    NULL COMMENT '驳回原因，通过时为 NULL',
    trace_id      VARCHAR(64)     NOT NULL COMMENT '请求 trace id',
    occurred_at   DATETIME(3)     NOT NULL COMMENT '动作发生时间',
    status        TINYINT         NOT NULL DEFAULT 0 COMMENT '投递状态：0 待投递 / 1 已投递 / 2 已放弃',
    retry_count   INT             NOT NULL DEFAULT 0 COMMENT '已失败重试次数',
    next_retry_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次投递时间',
    delivered_at  DATETIME(3)     NULL COMMENT '投递成功时间',
    last_error    VARCHAR(300)    NULL COMMENT '最近一次失败原因',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedupe (dedupe_key),
    KEY idx_status_next (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '退款审核审计投递箱';
