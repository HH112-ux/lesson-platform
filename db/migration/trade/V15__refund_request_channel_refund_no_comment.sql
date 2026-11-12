-- lp_oms 退款单渠道退款号注释（V15）
-- 仅修改 refund_request.channel_refund_no 的 COMMENT，不改类型、长度、可空性与默认值。
-- 原定义（V5）：channel_refund_no VARCHAR(64) NULL COMMENT '渠道退款号'
-- 新定义        ：channel_refund_no VARCHAR(64) NULL COMMENT '...（见下）'
-- 该列存的是我们生成的退款请求键（out_request_no），不是渠道侧退款单号：
--   * 支付宝没有独立退款单号，渠道侧确认退款只认 out_request_no；
--   * 该值与 refund_no 相同，重复存储是刻意的——让「渠道认哪个键」在数据行上直接可见，
--     不是可删除的冗余字段；
--   * 不得把它当作渠道返回的退款 id 去做对接或对账。
-- 回滚（仅本地/开发）：把 COMMENT 改回原文，不涉及数据。
--   ALTER TABLE refund_request
--       MODIFY COLUMN channel_refund_no VARCHAR(64) NULL COMMENT '渠道退款号';

ALTER TABLE refund_request
    MODIFY COLUMN channel_refund_no VARCHAR(64) NULL
        COMMENT '我们生成的退款请求键（即 out_request_no，与 refund_no 同值）；不是渠道侧退款单号，支付宝没有独立退款单号；该重复存储是刻意的，不可删';
