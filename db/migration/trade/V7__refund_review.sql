-- lp_oms 退款审核与准入（V7）
-- 退款单状态：0 待审核 / 1 审核通过（调渠道中）/ 2 已退款 / 3 驳回 / 4 退款失败。
-- 一笔订单同时只能存在一条进行中（0/1/2）的退款单；驳回（3）与失败（4）后允许重新申请，
-- 因此把 uk_order 换成只对进行中状态生效的生成列唯一键。
-- 金额单位仍为元（DECIMAL(10,2)），必须严格等于订单实付，不做比例计算。
-- 回滚（仅本地/开发）：
--   ALTER TABLE refund_request
--       DROP INDEX uk_active_order,
--       DROP COLUMN active_order_id,
--       DROP INDEX idx_status_created;
--   -- 先确认同一 order_id 没有多条进行中退款单，再恢复 uk_order
--   ALTER TABLE refund_request ADD UNIQUE KEY uk_order (order_id);
--   ALTER TABLE refund_request
--       DROP COLUMN review_reason,
--       DROP COLUMN reviewed_by,
--       DROP COLUMN reviewed_at,
--       DROP COLUMN fail_reason,
--       MODIFY COLUMN reason VARCHAR(200) NULL COMMENT '申请原因',
--       MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0 处理中 / 1 成功 / 2 失败';

ALTER TABLE refund_request
    MODIFY COLUMN reason VARCHAR(300) NULL COMMENT '申请原因，1-300 字',
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待审核 / 1 审核通过（调渠道中）/ 2 已退款 / 3 驳回 / 4 退款失败',
    ADD COLUMN review_reason VARCHAR(300) NULL COMMENT '审核意见，驳回时必填' AFTER reason,
    ADD COLUMN reviewed_by BIGINT UNSIGNED NULL COMMENT '审核人 user id' AFTER review_reason,
    ADD COLUMN reviewed_at DATETIME(3) NULL COMMENT '审核时间' AFTER reviewed_by,
    ADD COLUMN fail_reason VARCHAR(300) NULL COMMENT '渠道退款失败原因' AFTER channel_refund_no,
    ADD COLUMN active_order_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status IN (0, 1, 2) THEN order_id ELSE NULL END) STORED
        COMMENT '进行中退款单对应的订单，用于唯一键',
    DROP INDEX uk_order,
    ADD UNIQUE KEY uk_active_order (active_order_id),
    ADD KEY idx_status_created (status, created_at);
