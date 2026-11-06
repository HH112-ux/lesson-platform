-- lp_lms 权益失效状态（V5）
-- 退款只做软失效：保留权益行与学习记录，status 置 1；访问判定按未购处理。
-- 同一用户再次购买同一课程时，发放动作把该行重新置为生效，不新增行，uk_user_course 仍然兜底。
-- 回滚（仅本地/开发）：ALTER TABLE user_entitlement DROP INDEX idx_user_status, DROP COLUMN revoked_at, DROP COLUMN status;

ALTER TABLE user_entitlement
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0 生效 / 1 已退款失效' AFTER source,
    ADD COLUMN revoked_at DATETIME(3) NULL COMMENT '失效时间' AFTER granted_at,
    ADD KEY idx_user_status (user_id, status);
