-- lp_cms 教师入驻审核字段（V6）
-- 范围：teacher_profile 增加入驻审核状态、审核意见与审核留痕字段，供运营审核与建课前置校验使用。
-- 状态口径：0 待审核 / 1 已通过 / 2 已驳回；只有 1 已通过可以建课或编辑课程。
-- 存量处理：本迁移把全部存量教师置为 1 已通过，而不是 0 待审核。理由：
--   1) 存量教师（含演示教师）名下已有已上架课程与在售内容，置为待审核会立即阻断其编辑与建课，
--      使现有演示闭环与已上架内容无法维护；
--   2) 本版本之前不存在任何入驻申请记录，没有任何依据把既有教师判为未审核，按既成事实视为通过；
--   3) 新档案仍按列默认值 0 待审核进入运营队列，演示种子显式写入 1，保证演示教师可继续建课。
-- 审核留痕（操作人、时间、前后状态）暂存在本表；L1-7 建统一审计表时再回填，本版本不丢信息。
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS；本迁移由 Flyway 版本化执行一次。
-- 回滚：ALTER TABLE teacher_profile
--       DROP KEY idx_audit_status,
--       DROP COLUMN submitted_at,
--       DROP COLUMN audited_at,
--       DROP COLUMN audited_by,
--       DROP COLUMN audit_from_status,
--       DROP COLUMN audit_remark,
--       DROP COLUMN audit_status;

ALTER TABLE teacher_profile
    ADD COLUMN audit_status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待审核 / 1 已通过 / 2 已驳回',
    ADD COLUMN audit_remark      VARCHAR(300)    NULL COMMENT '审核意见，驳回时必填',
    ADD COLUMN audit_from_status TINYINT         NULL COMMENT '审核前状态：固定 0 待审核',
    ADD COLUMN audited_by        BIGINT UNSIGNED NULL COMMENT '审核人用户 id',
    ADD COLUMN audited_at        DATETIME(3)     NULL COMMENT '审核时间',
    ADD COLUMN submitted_at      DATETIME(3)     NULL COMMENT '入驻资料提交时间',
    ADD KEY idx_audit_status (audit_status, created_at);

UPDATE teacher_profile
SET audit_status      = 1,
    audit_from_status = 0,
    audited_at        = created_at
WHERE audit_status = 0;
