-- lp_cms 统一操作审计（V7）
-- 范围严格限定三类审核动作：课程审核、退款审核、教师审核，不覆盖其他管理操作。
-- 动作类型：0 课程通过 / 1 课程驳回 / 2 退款通过 / 3 退款驳回 / 4 教师通过 / 5 教师驳回。
-- 对象类型：0 课程 / 1 退款 / 2 教师。
-- 操作人角色：0 运营 / 1 管理员。
-- 前后状态取值随对象类型解释：
--   课程  1 待审核 -> 2 已上架（通过）或 0 草稿（驳回）；
--   退款  0 待审核 -> 1 审核通过（调渠道中，通过）或 3 驳回；
--   教师  0 待审核 -> 1 已通过（通过）或 2 已驳回。
-- 事务边界：课程审核与教师审核的状态更新都在 lp_cms，审计行与业务更新同事务写入；
--   退款审核的状态更新在 lp_oms，无法跨库同事务，改为在 lp_oms.audit_outbox 同一事务写投递意图，
--   由 trade-service 中继投递到本表（至少一次，dedupe_key 唯一键去重），详见 docs/engineering-plan.md 5.9。
-- 与 course_review_audit 的关系：历史表保留且继续双写，不迁移历史数据（旧行没有 traceId 与操作人角色）；
--   单课程历史查询仍走 course_review_audit，跨域统一视图走本表。
-- 回滚：DROP TABLE admin_audit_log;

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '操作人 user id',
    operator_role TINYINT         NOT NULL COMMENT '操作人角色：0 运营 / 1 管理员',
    action_type   TINYINT         NOT NULL COMMENT '动作类型：0 课程通过 / 1 课程驳回 / 2 退款通过 / 3 退款驳回 / 4 教师通过 / 5 教师驳回',
    object_type   TINYINT         NOT NULL COMMENT '对象类型：0 课程 / 1 退款 / 2 教师',
    object_id     BIGINT UNSIGNED NOT NULL COMMENT '对象主键，含义由 object_type 决定',
    from_status   TINYINT         NOT NULL COMMENT '变更前状态，取值随 object_type 解释',
    to_status     TINYINT         NOT NULL COMMENT '变更后状态，取值随 object_type 解释',
    remark        VARCHAR(300)    NULL COMMENT '备注或驳回原因',
    trace_id      VARCHAR(64)     NOT NULL COMMENT '请求 trace id，用于串联业务日志',
    dedupe_key    VARCHAR(128)    NULL COMMENT '跨服务投递幂等键；本地同事务写入为 NULL',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '动作发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedupe (dedupe_key),
    KEY idx_object_created (object_type, object_id, created_at),
    KEY idx_operator_created (operator_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '统一操作审计：课程审核 / 退款审核 / 教师审核';
