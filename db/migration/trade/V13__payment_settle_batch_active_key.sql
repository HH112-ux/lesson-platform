-- Settlement batch history for SPEC-TRADE-014 AC-3 (bill day close).
--
-- A voided batch must stay queryable, while the same channel and bill day can be closed again as a
-- new batch. A plain unique key on (channel, bill_date) forbids that second row, so it is replaced
-- by a unique key on active_day_key that only closed rows carry.
--
-- active_day_key is written by the close statement as "channel-billDate" and cleared to NULL by
-- the void statement. MySQL unique indexes allow multiple NULLs, so any number of voided rows can
-- coexist with one active row per day. The column is deliberately not a generated column: H2, used
-- by the persistence test, throws on ON DUPLICATE KEY UPDATE over a generated-column unique key.
--
-- Rollback: drop uk_active_day and active_day_key, then restore uk_channel_date. The restore only
-- succeeds before a second batch exists for any day; otherwise remove or archive the extra rows
-- first, because a destructive delete of settlement history is not acceptable.
--
-- Deploy order: run this script only after every trade-service instance runs the release that
-- writes and clears active_day_key. A pre-release instance inserts status=1 rows without the key,
-- and its void statement leaves the key in place, so a later close would revive the voided row.
ALTER TABLE payment_settle_batch
    DROP INDEX uk_channel_date,
    ADD COLUMN active_day_key VARCHAR(64) NULL
        COMMENT 'Closed row key channel-billDate; NULL for voided rows';

UPDATE payment_settle_batch
   SET active_day_key = CONCAT(channel, '-', bill_date)
 WHERE status = 1;

ALTER TABLE payment_settle_batch
    ADD UNIQUE KEY uk_active_day (active_day_key);
