-- lp_oms 交易金额边界约束（V8）
-- 范围：trade_order、trade_order_item、cart_item、coupon、payment_record、refund_request。
-- 背景：金额此前只靠应用层校验，负价、优惠超额、订单金额不平衡都可能在绕过接口时写入，
-- 进而把订单夹成 0 元并发放权益。本迁移把边界下沉到数据库。
-- 存量核对（2026-11-09，本地 lp_oms）：负金额 0 行；discount>total 或
-- pay<>total-discount 0 行；折扣率越界 0 行；退款金额超过实付 0 行，可直接加约束。
-- 注意：MySQL 不支持 ADD CONSTRAINT IF NOT EXISTS，本迁移由 Flyway 版本化执行一次。
-- 回滚（仅本地/开发）：
--   ALTER TABLE trade_order DROP CHECK chk_trade_order_total_non_negative,
--       DROP CHECK chk_trade_order_discount_non_negative,
--       DROP CHECK chk_trade_order_pay_non_negative,
--       DROP CHECK chk_trade_order_discount_within_total,
--       DROP CHECK chk_trade_order_amount_balance;
--   ALTER TABLE trade_order_item DROP CHECK chk_order_item_price_non_negative,
--       DROP CHECK chk_order_item_discount_non_negative,
--       DROP CHECK chk_order_item_actual_non_negative,
--       DROP CHECK chk_order_item_amount_balance;
--   ALTER TABLE cart_item DROP CHECK chk_cart_item_price_non_negative;
--   ALTER TABLE coupon DROP CHECK chk_coupon_threshold_non_negative,
--       DROP CHECK chk_coupon_discount_non_negative,
--       DROP CHECK chk_coupon_rate_range;
--   ALTER TABLE payment_record DROP CHECK chk_payment_amount_non_negative;
--   ALTER TABLE refund_request DROP CHECK chk_refund_amount_non_negative;

ALTER TABLE trade_order
    ADD CONSTRAINT chk_trade_order_total_non_negative CHECK (total_amount >= 0),
    ADD CONSTRAINT chk_trade_order_discount_non_negative CHECK (discount_amount >= 0),
    ADD CONSTRAINT chk_trade_order_pay_non_negative CHECK (pay_amount >= 0),
    ADD CONSTRAINT chk_trade_order_discount_within_total CHECK (discount_amount <= total_amount),
    ADD CONSTRAINT chk_trade_order_amount_balance CHECK (pay_amount = total_amount - discount_amount);

ALTER TABLE trade_order_item
    ADD CONSTRAINT chk_order_item_price_non_negative CHECK (price >= 0),
    ADD CONSTRAINT chk_order_item_discount_non_negative CHECK (discount_amount >= 0),
    ADD CONSTRAINT chk_order_item_actual_non_negative CHECK (actual_amount >= 0),
    ADD CONSTRAINT chk_order_item_amount_balance CHECK (actual_amount = price - discount_amount);

ALTER TABLE cart_item
    ADD CONSTRAINT chk_cart_item_price_non_negative CHECK (price_snapshot >= 0);

ALTER TABLE coupon
    ADD CONSTRAINT chk_coupon_threshold_non_negative CHECK (threshold_amount >= 0),
    ADD CONSTRAINT chk_coupon_discount_non_negative
        CHECK (discount_amount IS NULL OR discount_amount >= 0),
    ADD CONSTRAINT chk_coupon_rate_range
        CHECK (discount_rate IS NULL OR (discount_rate > 0 AND discount_rate < 1));

ALTER TABLE payment_record
    ADD CONSTRAINT chk_payment_amount_non_negative CHECK (amount >= 0);

ALTER TABLE refund_request
    ADD CONSTRAINT chk_refund_amount_non_negative CHECK (amount >= 0);
