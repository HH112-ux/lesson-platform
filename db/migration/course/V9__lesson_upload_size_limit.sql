-- lp_cms 课时上传声明大小上限放宽（V9）
-- 依据：docs/engineering-plan.md 第 10 章「单个视频不得超过 2 GiB（2147483648 字节）」。
-- 变更：chk_lesson_upload_size 从 200 MiB（209715200 字节）放宽到 2 GiB（2147483648 字节）。
-- 判定来源：接口层的 lp.course.upload.max-size-bytes 才是唯一判定入口；本 CHECK 只是最后兜底，
--   与配置同源，两者必须一致；再次调整上限须新增迁移，不得修改本文件。
-- 执行窗口：ALTER TABLE 只改 CHECK 元数据，不重建表、不锁业务数据，可在低峰期滚动发布窗口执行。
-- 执行后校验：
--   SHOW CREATE TABLE lesson_upload\G
--   -- 期望 chk_lesson_upload_size 为 (expected_size_bytes between 1 and 2147483648)
-- 回滚：CHECK 不能原地回退，须新增一条把上限降回 209715200 的迁移；执行前必须先确认没有
--   expected_size_bytes > 209715200 的待上传会话，否则 DDL 会失败：
--   SELECT COUNT(*) FROM lesson_upload WHERE status = 0 AND expected_size_bytes > 209715200;

ALTER TABLE lesson_upload
    DROP CHECK chk_lesson_upload_size;

ALTER TABLE lesson_upload
    ADD CONSTRAINT chk_lesson_upload_size CHECK (expected_size_bytes BETWEEN 1 AND 2147483648);
