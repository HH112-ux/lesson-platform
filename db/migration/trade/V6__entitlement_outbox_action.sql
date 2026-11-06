-- lp_oms 权益 outbox 增加动作（V6）
-- 退款需要在同一张 outbox 表里撤销权益，复用重试、退避、死信与运营重放。
-- 唯一键从 (order_id, course_id) 扩为 (order_id, course_id, action)，使同一订单既能发放也能撤销。
-- 回滚（仅本地/开发）：ALTER TABLE entitlement_outbox DROP INDEX uk_order_course_action,
--   ADD UNIQUE KEY uk_order_course (order_id, course_id), DROP COLUMN action;
-- 注意：若已存在 revoke 行，回滚前必须先处理这些行，否则唯一键会冲突。

ALTER TABLE entitlement_outbox
    ADD COLUMN action TINYINT NOT NULL DEFAULT 0 COMMENT '0 发放 / 1 撤销' AFTER course_id,
    DROP INDEX uk_order_course,
    ADD UNIQUE KEY uk_order_course_action (order_id, course_id, action);
