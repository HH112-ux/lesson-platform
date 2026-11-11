-- lp_oms 支付回调异常记录（V14）：验签通过但无法自动入账的回调不丢弃、不按回调金额改单，落可追溯记录并打 ERROR 告警。纯增量建表；回滚：DROP TABLE payment_notify_anomaly;

CREATE TABLE IF NOT EXISTS payment_notify_anomaly (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel          TINYINT         NOT NULL COMMENT '支付渠道：1 支付宝 / 2 微信',
    order_no         VARCHAR(64)     NOT NULL COMMENT '回调携带的订单号',
    channel_trade_no VARCHAR(64)     NOT NULL COMMENT '渠道交易号',
    expected_amount  DECIMAL(12,2)   NULL COMMENT '本地应付金额（元），订单不存在时为空',
    notified_amount  DECIMAL(12,2)   NULL COMMENT '回调金额（元）',
    trade_status     VARCHAR(32)     NULL COMMENT '渠道交易状态原文',
    reason           TINYINT         NOT NULL COMMENT '1 金额不一致 / 2 订单不存在 / 3 订单状态不可推进（已支付或关闭，需人工退款）',
    detail           VARCHAR(300)    NULL COMMENT '补充说明，如本地订单或支付状态',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_trade_reason (channel, channel_trade_no, reason),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '支付回调异常记录';
