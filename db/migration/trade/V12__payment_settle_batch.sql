-- lp_oms 支付日结对账批次（V12）
-- 对应 SPEC-TRADE-014 AC-3：差异未处置时阻断日结，处置后封账并汇总当日净额。
-- 口径（2026-11-10 冻结）：
--   1. 首版只支持支付宝（channel=1），一个账单日一条批次；支付账单与退款账单都必须是已导入
--      （payment_reconcile.status=1 或 3）才允许日结。
--   2. 日结 = 日终对账批次封账 + 净额汇总，不含手续费、不含分账、不含教师结算、不产生任何
--      资金动作；渠道金额取 payment_reconcile 的渠道聚合，本地金额取其本地聚合（来自
--      payment_record 与 refund_request）。不设 fee 列。
--   3. 日结后该账单日锁定：账单导入与重跑返回 40901；作废后解锁。
--   4. 作废只把批次置 status=2 并记录作废人与时间；重新日结复用同一行（uk_channel_date），
--      覆盖 closed_by/closed_at 并清空 void_by/void_at，行始终表达该账单日的当前状态。
-- 回滚（仅本地/开发）：DROP TABLE payment_settle_batch;

CREATE TABLE IF NOT EXISTS payment_settle_batch (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel               TINYINT         NOT NULL COMMENT '1 支付宝（首版唯一启用）/ 2 微信（历史，不新增日结）',
    bill_date             DATE            NOT NULL COMMENT '账单日（Asia/Shanghai）',
    channel_pay_count     INT             NOT NULL DEFAULT 0 COMMENT '渠道支付账单笔数',
    channel_pay_amount    DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '渠道支付账单金额（元）',
    channel_refund_count  INT             NOT NULL DEFAULT 0 COMMENT '渠道退款账单笔数',
    channel_refund_amount DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '渠道退款账单金额（元）',
    local_pay_count       INT             NOT NULL DEFAULT 0 COMMENT '本地支付笔数',
    local_pay_amount      DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '本地支付金额（元）',
    local_refund_count    INT             NOT NULL DEFAULT 0 COMMENT '本地退款笔数',
    local_refund_amount   DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '本地退款金额（元）',
    net_channel_amount    DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '渠道净额（支付金额-退款金额，元）',
    net_local_amount      DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '本地净额（支付金额-退款金额，元）',
    diff_count            INT             NOT NULL DEFAULT 0 COMMENT '封账时支付与退款两账单一并的差异笔数',
    diff_amount           DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT '封账时差异金额合计（元）',
    status                TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已日结 / 2 已作废',
    closed_by             BIGINT UNSIGNED NOT NULL COMMENT '日结操作人 user id',
    closed_at             DATETIME(3)     NOT NULL COMMENT '日结时间',
    void_by               BIGINT UNSIGNED NULL COMMENT '作废操作人 user id，未作废为 NULL',
    void_at               DATETIME(3)     NULL COMMENT '作废时间，未作废为 NULL',
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_date (channel, bill_date),
    KEY idx_status_date (status, bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付日结对账批次';
