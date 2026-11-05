-- lp_oms 交易表（V3）
-- 增量：为 trade_order 补 closed_at；新建 payment_record 支付记录。
-- trade_order / trade_order_item 已由 V2 建好，本迁移不再重复建表。
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，ALTER 只允许执行一次，Flyway 的版本记录保证幂等；
-- 若手工重复执行会因列已存在而报错，属预期行为。

ALTER TABLE trade_order
    ADD COLUMN closed_at DATETIME(3) NULL COMMENT '关闭时间';

-- 0 元订单在创建时即完成，没有支付截止时间，因此该列必须允许 NULL。
ALTER TABLE trade_order
    MODIFY COLUMN pay_expire_at DATETIME(3) NULL COMMENT '支付截止（下单后 30 分钟；即时完成订单为 NULL）';

CREATE TABLE IF NOT EXISTS payment_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id         BIGINT UNSIGNED NOT NULL COMMENT 'trade_order.id',
    pay_no           VARCHAR(64)     NOT NULL COMMENT '平台支付单号（LP + 日期 + 序列）',
    channel          TINYINT         NOT NULL COMMENT '1 支付宝 / 2 微信',
    pay_scene        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 Web 页面支付 / 2 小程序 JSAPI',
    amount           DECIMAL(10,2)   NOT NULL COMMENT '金额（元），回调时与订单应付金额比对',
    status           TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待支付 / 1 成功 / 2 已关闭 / 3 已退款 / 4 退款中 / 5 退款失败',
    channel_trade_no VARCHAR(64)     NULL COMMENT '渠道交易号：支付宝 trade_no / 微信 transaction_id',
    channel_app_id   VARCHAR(64)     NULL COMMENT '渠道侧 AppID',
    channel_mch_id   VARCHAR(64)     NULL COMMENT '渠道商户号',
    payer_openid     VARCHAR(64)     NULL COMMENT '微信付款人 openid（L2，不落日志）',
    paid_at          DATETIME(3)     NULL COMMENT '渠道支付成功时间',
    raw_digest       VARCHAR(64)     NULL COMMENT '回调原文摘要（不存原文）',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    UNIQUE KEY uk_channel_trade (channel, channel_trade_no),
    KEY idx_order (order_id),
    KEY idx_status_paid (status, paid_at),
    KEY idx_channel_paid (channel, paid_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付记录';

-- 回滚方式（仅限本地/开发环境，生产禁止破坏性回滚）：
-- 1) DROP TABLE payment_record;
-- 2) ALTER TABLE trade_order DROP COLUMN closed_at;
-- 3) ALTER TABLE trade_order MODIFY COLUMN pay_expire_at DATETIME(3) NOT NULL;
-- 生产回滚以代码 revert 为准，表与列保留并由后续迁移兼容。
