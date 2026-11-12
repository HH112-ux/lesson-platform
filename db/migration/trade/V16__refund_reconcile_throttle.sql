-- lp_oms 退款只读对账（V16）
-- 目的：为退款对账扫描提供节流与日志去重所需的记账字段，不改变退款单状态语义。
--   * reconcile_attempts      已查询次数，用于退避（1 次后 1 小时，2 次后 6 小时）；
--   * reconcile_checked_at    最近一次查询时间，扫描按它做「最久未查优先」排序；
--   * reconcile_finding       最近一次对账结论，用于「只在状态变化时打日志」；
--   * idx_reconcile_scan      扫描条件 status IN (1,5) + reconcile_checked_at。
-- 回滚：删除索引与三列，对账任务退化为无状态扫描，无数据丢失风险。
--   ALTER TABLE refund_request DROP INDEX idx_reconcile_scan,
--       DROP COLUMN reconcile_finding, DROP COLUMN reconcile_checked_at,
--       DROP COLUMN reconcile_attempts;

ALTER TABLE refund_request
    ADD COLUMN reconcile_attempts INT NOT NULL DEFAULT 0
        COMMENT '退款只读对账查询次数，用于退避（0 未查，1 后 1 小时，2 次后 6 小时）'
        AFTER channel_refund_no,
    ADD COLUMN reconcile_checked_at DATETIME NULL
        COMMENT '最近一次退款只读对账查询时间'
        AFTER reconcile_attempts,
    ADD COLUMN reconcile_finding VARCHAR(32) NULL
        COMMENT '最近一次对账结论，用于日志去重：CHANNEL_DONE / CHANNEL_NOT_FOUND / CHANNEL_OTHER / QUERY_FAILED / PAYMENT_UNSETTLED'
        AFTER reconcile_checked_at,
    ADD INDEX idx_reconcile_scan (status, reconcile_checked_at);
