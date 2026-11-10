-- lp_oms 支付对账（V11）
-- 对应 SPEC-TRADE-014：T+1 账单导入、逐日聚合、差异落库、人工标记已处理。
-- 当前只支持运营手动导入 CSV（UTF-8），真实支付宝账单 API 客户端尚未接入，因此不建自动拉单任务。
-- 不引入对象存储：导入的 CSV 原文存 raw_bill，差异明细（含本地记录定位）存 diff_detail(JSON)；
-- 文档中的 diff_detail_ref 列保留但当前为空，供真实账单原始文件接入后使用。
-- 回滚（仅本地/开发）：DROP TABLE payment_reconcile;

CREATE TABLE IF NOT EXISTS payment_reconcile (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel          TINYINT         NOT NULL COMMENT '1 支付宝（首版唯一启用）/ 2 微信（历史，不新增对账任务）',
    bill_date        DATE            NOT NULL COMMENT '账单日（Asia/Shanghai）',
    bill_type        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 支付账单 / 2 退款账单',
    channel_count    INT             NOT NULL DEFAULT 0 COMMENT '渠道账单笔数',
    channel_amount   DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '渠道账单金额（元）',
    local_count      INT             NOT NULL DEFAULT 0 COMMENT '本地支付/退款笔数',
    local_amount     DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '本地金额（元）',
    diff_count       INT             NOT NULL DEFAULT 0 COMMENT '差异笔数（长款+短款）',
    diff_amount      DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '差异金额（元，正=渠道多收）',
    diff_detail_ref  VARCHAR(255)    NULL COMMENT '差异明细对象键；当前明细落 diff_detail，本列预留',
    raw_bill         MEDIUMTEXT      NULL COMMENT '导入的账单 CSV 原文（UTF-8），用于重跑对账',
    diff_detail      MEDIUMTEXT      NULL COMMENT '差异明细 JSON 数组，含本地 payment_record/refund_request 定位信息',
    status           TINYINT         NOT NULL DEFAULT 0 COMMENT '0 对账中 / 1 一致 / 2 有差异待处理 / 3 已处理',
    handled_by       BIGINT UNSIGNED NULL COMMENT '标记已处理的运营用户 id',
    handled_at       DATETIME(3)     NULL COMMENT '标记已处理时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_date_type (channel, bill_date, bill_type),
    KEY idx_status_date (status, bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分渠道对账记录';
