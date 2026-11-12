-- lp_oms 退款结算证据（V17）
-- 目的：记录「谁把这张退款单收敛到已退款、什么时候」，覆盖人工审核同步结算与自动收敛两条路径。
--   * settled_by   收敛来源，review:{用户id} 表示审核人，job 表示对账任务自动收敛；
--   * settled_at   收敛为已退款的时间，与 refunded_at 同值，单独存列是为了审计口径不依赖语义变更。
-- 审计口径：自动收敛不写 audit_outbox，退款单的审计依据就是
--   settled_by / settled_at / reconcile_finding / reconcile_checked_at / reconcile_attempts 这五个字段。
-- 两列均可空，历史行无需回填；只有 status 从 1/5 收敛到 2 时才会写入，碰不到已结算记录。
-- 回滚：删除两列，历史结算行退回只靠 refunded_at 判断，无数据丢失风险。
--   ALTER TABLE refund_request DROP COLUMN settled_by, DROP COLUMN settled_at;

ALTER TABLE refund_request
    ADD COLUMN settled_by VARCHAR(64) NULL
        COMMENT '退款收敛来源：review:{用户id} 人工审核 / job 对账自动收敛'
        AFTER reconcile_finding,
    ADD COLUMN settled_at DATETIME NULL
        COMMENT '退款收敛为已退款的时间，与 refunded_at 同值'
        AFTER settled_by;
