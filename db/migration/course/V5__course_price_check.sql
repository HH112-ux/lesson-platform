-- lp_cms 课程价格非负约束（V5）
-- 范围：course.price 与 course.original_price 不允许为负数。
-- 背景：教师端与审核端此前都没有兜住负价，交易侧把负价夹成 0 元后可按免费课领权益。
-- 注意：MySQL 不支持 ADD CONSTRAINT IF NOT EXISTS；本迁移由 Flyway 版本化执行一次。
-- 回滚：ALTER TABLE course DROP CHECK chk_course_price_non_negative,
--       DROP CHECK chk_course_original_price_non_negative;

ALTER TABLE course
    ADD CONSTRAINT chk_course_price_non_negative CHECK (price >= 0),
    ADD CONSTRAINT chk_course_original_price_non_negative
        CHECK (original_price IS NULL OR original_price >= 0);
