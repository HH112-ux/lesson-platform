#!/usr/bin/env bash
#
# One command acceptance run: start the stack, smoke the purchase chain, run the outbox
# fault drill and the refund drill, then remove the data those runs created.
#
# Usage:
#   bash scripts/verify-all.sh
#
# Every step writes a log under logs/verify-<timestamp>/. The run stops at the first FAIL,
# prints the failing log path and keeps the drill data for inspection. When every step
# passes the drill data is deleted so the next run starts from the same state.
#
# Environment:
#   LP_VERIFY_SKIP_UP=1   reuse the running stack instead of running dev-up.sh (SKIPPED step)
#   LP_VERIFY_LOG_DIR=... override the log directory
#
# Requires Docker, JDK 17, Node 20, pnpm and jq on the host, same as dev-up.sh.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/infra/.env")
MYSQL_ROOT_PASSWORD="${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}"

LOG_DIR="${LP_VERIFY_LOG_DIR:-$ROOT/logs/verify-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR" || { echo "FAIL cannot create $LOG_DIR" >&2; exit 1; }

STEP_NAMES=()
STEP_STATUSES=()
STEP_SECONDS=()
STEP_LOGS=()
FAILED_STEP=""

SHELL_BIN="/bin/bash"
TOTAL_START="$(date +%s)"

printf 'verify-all: logs in %s\n' "$LOG_DIR"
printf 'verify-all: skip stack start = %s\n' "${LP_VERIFY_SKIP_UP:-0}"

# run_step NAME COMMAND...
# Runs one step, tees its output to NAME.log and records PASS/FAIL with the duration.
run_step() {
  local name="$1"
  shift
  local log="$LOG_DIR/$name.log"
  local start now status

  printf '\n===== %s =====\n' "$name"
  start="$(date +%s)"
  if "$@" 2>&1 | tee "$log"; then
    status="PASS"
  else
    status="FAIL"
    FAILED_STEP="$name"
  fi
  now="$(date +%s)"

  STEP_NAMES+=("$name")
  STEP_STATUSES+=("$status")
  STEP_SECONDS+=("$((now - start))")
  STEP_LOGS+=("$log")
}

skip_step() {
  local name="$1" reason="$2"
  printf '\n===== %s =====\nSKIPPED %s\n' "$name" "$reason"
  STEP_NAMES+=("$name")
  STEP_STATUSES+=("SKIPPED")
  STEP_SECONDS+=(0)
  STEP_LOGS+=("-")
}

print_summary() {
  local total now
  now="$(date +%s)"
  total=$((now - TOTAL_START))
  printf '\n===== summary =====\n'
  local i
  for i in "${!STEP_NAMES[@]}"; do
    printf '%-10s %-8s %5ss  %s\n' \
      "${STEP_NAMES[$i]}" "${STEP_STATUSES[$i]}" "${STEP_SECONDS[$i]}" "${STEP_LOGS[$i]}"
  done
  printf '%-10s %-8s %5ss\n' "total" "-" "$total"
}

abort_with_log() {
  printf '\nFAIL %s\n' "$1" >&2
  printf 'log: %s\n' "$LOG_DIR/$1.log" >&2
  printf 'last 40 lines of the failing log:\n' >&2
  tail -40 "$LOG_DIR/$1.log" >&2 || true
  printf '\nDrill data was kept in the database for inspection.\n' >&2
  print_summary
  exit 1
}

# Removes only rows whose course title starts with one of the drill prefixes, plus the unpaid
# combined checkout orders whose idempotency key carries the smoke prefix. Demo seed courses
# use different titles, so the seed data is never touched.
cleanup_drill_data() {
  # A default schema is required for the temporary tables below, and the connection must
  # declare utf8mb4 or the Chinese drill titles would never match in LIKE.
  "${COMPOSE[@]}" exec -T mysql mysql --default-character-set=utf8mb4 \
    -uroot -p"$MYSQL_ROOT_PASSWORD" lp_oms <<'SQL'
CREATE TEMPORARY TABLE drill_course (id BIGINT UNSIGNED PRIMARY KEY);
INSERT IGNORE INTO drill_course (id)
SELECT id FROM lp_cms.course
WHERE title LIKE '冒烟验证课程 %'
   OR title LIKE 'outbox 故障演练 %'
   OR title LIKE '退款闭环演练 %'
   OR title LIKE '退款超额演练 %';

CREATE TEMPORARY TABLE drill_order (id BIGINT UNSIGNED PRIMARY KEY);
INSERT IGNORE INTO drill_order (id)
SELECT DISTINCT o.id
FROM lp_oms.trade_order o
INNER JOIN lp_oms.trade_order_item i ON i.order_id = o.id
WHERE i.course_id IN (SELECT id FROM drill_course);

DELETE FROM lp_oms.refund_request WHERE order_id IN (SELECT id FROM drill_order);
DELETE FROM lp_oms.payment_record WHERE order_id IN (SELECT id FROM drill_order);
DELETE FROM lp_oms.entitlement_outbox
 WHERE order_id IN (SELECT id FROM drill_order)
    OR course_id IN (SELECT id FROM drill_course);
DELETE FROM lp_oms.trade_order_item WHERE order_id IN (SELECT id FROM drill_order);
DELETE FROM lp_oms.trade_order WHERE id IN (SELECT id FROM drill_order);

DELETE FROM lp_lms.learning_progress WHERE course_id IN (SELECT id FROM drill_course);
DELETE FROM lp_lms.user_entitlement WHERE course_id IN (SELECT id FROM drill_course);

DELETE FROM lp_cms.course_review_audit WHERE course_id IN (SELECT id FROM drill_course);
DELETE FROM lp_cms.course_lesson WHERE course_id IN (SELECT id FROM drill_course);
DELETE FROM lp_cms.course_chapter WHERE course_id IN (SELECT id FROM drill_course);
DELETE FROM lp_cms.course WHERE id IN (SELECT id FROM drill_course);

SELECT CONCAT('cleanup removed ', COUNT(*), ' drill courses') AS result FROM drill_course;

-- Combined checkout orders buy demo seed courses, so the drill course join cannot find them.
-- The idempotency key prefix identifies them, and only unpaid rows are removed.
CREATE TEMPORARY TABLE combo_order (id BIGINT UNSIGNED PRIMARY KEY);
INSERT IGNORE INTO combo_order (id)
SELECT id FROM lp_oms.trade_order
WHERE idempotency_key LIKE 'combo-smoke-%' AND status = 0;

SELECT CONCAT('cleanup found ', COUNT(*), ' combined smoke orders') AS result FROM combo_order;

DELETE i FROM lp_oms.trade_order_item i
INNER JOIN combo_order c ON c.id = i.order_id;
DELETE o FROM lp_oms.trade_order o
INNER JOIN combo_order c ON c.id = o.id;

DROP TEMPORARY TABLE combo_order;
DROP TEMPORARY TABLE drill_order;
DROP TEMPORARY TABLE drill_course;
SQL
}

if [ "${LP_VERIFY_SKIP_UP:-0}" = "1" ]; then
  skip_step "dev-up" "LP_VERIFY_SKIP_UP=1, reusing the running stack"
else
  run_step "dev-up" "$SHELL_BIN" "$ROOT/scripts/dev-up.sh"
  [ "$FAILED_STEP" = "dev-up" ] && abort_with_log "dev-up"
fi

run_step "smoke" "$SHELL_BIN" "$ROOT/scripts/smoke.sh"
[ "$FAILED_STEP" = "smoke" ] && abort_with_log "smoke"

run_step "outbox-drill" "$SHELL_BIN" "$ROOT/scripts/outbox-fault-drill.sh"
[ "$FAILED_STEP" = "outbox-drill" ] && abort_with_log "outbox-drill"

run_step "refund-drill" "$SHELL_BIN" "$ROOT/scripts/refund-drill.sh"
[ "$FAILED_STEP" = "refund-drill" ] && abort_with_log "refund-drill"

run_step "cleanup" cleanup_drill_data
[ "$FAILED_STEP" = "cleanup" ] && abort_with_log "cleanup"

print_summary
printf '\nVERIFY OK: stack, smoke, outbox drill and refund drill all passed; drill data removed.\n'
exit 0
