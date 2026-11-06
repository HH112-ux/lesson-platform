-- lp_oms 退款单（V5）
-- 首版只支持全额退款：amount 必须等于订单实付，uk_order 保证一笔订单最多一条退款单。
-- 退款走渠道同步调用，权益撤销走 entitlement_outbox（action=1），同事务提交。
-- 回滚（仅本地/开发）：DROP TABLE refund_request;

CREATE TABLE IF NOT EXISTS refund_request (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_no         VARCHAR(40)     NOT NULL COMMENT '退款单号，同时作为渠道幂等键',
    order_id          BIGINT UNSIGNED NOT NULL COMMENT 'trade_order.id',
    payment_id        BIGINT UNSIGNED NULL COMMENT '成功支付记录 id',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '学员',
    channel           TINYINT         NOT NULL COMMENT '1 支付宝 / 2 微信（历史）',
    amount            DECIMAL(10, 2)  NOT NULL COMMENT '退款金额（元），不得超过实付',
    reason            VARCHAR(200)    NULL COMMENT '申请原因',
    status            TINYINT         NOT NULL DEFAULT 0 COMMENT '0 处理中 / 1 成功 / 2 失败',
    channel_refund_no VARCHAR(64)     NULL COMMENT '渠道退款号',
    refunded_at       DATETIME(3)     NULL COMMENT '退款成功时间',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_order (order_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '退款单';
