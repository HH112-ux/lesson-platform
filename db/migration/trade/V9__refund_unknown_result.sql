-- lp_oms 退款调用结果未知（V9）
-- 退款单状态新增 5 结果未知待确认：渠道调用超时、连接重置或响应不可解析时使用。
-- 状态 5 与 0/1/2 一样属于进行中，订单保持在 4 退款中，禁止用户再次申请；
-- 只有渠道明确拒绝才落 4 退款失败并把订单放回 1 已支付。
-- 回滚（仅本地/开发）：先确认没有 status = 5 的退款单，再执行
--   ALTER TABLE refund_request
--       DROP INDEX uk_active_order,
--       DROP COLUMN active_order_id,
--       MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
--           COMMENT '0 待审核 / 1 审核通过（调渠道中）/ 2 已退款 / 3 驳回 / 4 退款失败',
--       ADD COLUMN active_order_id BIGINT UNSIGNED
--           GENERATED ALWAYS AS (CASE WHEN status IN (0, 1, 2) THEN order_id ELSE NULL END) STORED
--           COMMENT '进行中退款单对应的订单，用于唯一键',
--       ADD UNIQUE KEY uk_active_order (active_order_id);

-- 先拆掉依赖旧状态集合的生成列与唯一键，避免修改 status 注释时受生成列依赖限制。
ALTER TABLE refund_request
    DROP INDEX uk_active_order,
    DROP COLUMN active_order_id;

ALTER TABLE refund_request
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '0 待审核 / 1 审核通过（调渠道中）/ 2 已退款 / 3 驳回 / 4 退款失败 / 5 结果未知待确认',
    ADD COLUMN active_order_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status IN (0, 1, 2, 5) THEN order_id ELSE NULL END) STORED
        COMMENT '进行中退款单对应的订单（含结果未知），用于唯一键',
    ADD UNIQUE KEY uk_active_order (active_order_id);
