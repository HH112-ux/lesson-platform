#!/usr/bin/env bash
#
# Entitlement outbox fault drill.
#
# Proves the trade -> learn entitlement path survives a learn outage instead of only
# looking correct in unit tests:
#   1. a paid order is still accepted while learn-service is down;
#   2. the outbox retries with backoff and never gives up early;
#   3. the relay delivers automatically once learn-service recovers;
#   4. a repeated grant is absorbed by uk_user_course;
#   5. a row past the retry limit becomes failed, shows up in the gauges and can be requeued.
#
# Usage:
#   bash scripts/outbox-fault-drill.sh
#
# Requires the local stack (scripts/dev-up.sh) and jq. The script temporarily recreates
# trade-service with LP_PAY_MOCK_ENABLED=true and restores it on exit. Every step prints
# the observed values; the script exits non-zero on the first failed assertion.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/infra/.env")

BASE="${LP_DRILL_BASE:-http://localhost:8080}"
TRADE_INTERNAL="${LP_TRADE_INTERNAL:-http://localhost:8103}"
LEARN_INTERNAL="${LP_LEARN_INTERNAL:-http://localhost:8104}"
PASSWORD="${LP_DRILL_PASSWORD:-Demo@123456}"
INTERNAL_TOKEN="${LP_INTERNAL_TOKEN:-lp_internal_dev_token}"
MYSQL_ROOT_PASSWORD="${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}"

RUN_ID="$(date +%Y%m%d%H%M%S)"
RUN_KEY="drill-$RUN_ID"
COURSE_TITLE="outbox 故障演练 $RUN_ID"

HTTP_STATUS=""
BODY=""
FAILURES=0
MOCK_TOGGLED=0

command -v jq >/dev/null 2>&1 || { echo "FAIL jq is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "FAIL docker is required"; exit 1; }

step() { printf '\n[%s] %s\n' "$1" "$2"; }
pass() { printf '  PASS %s\n' "$1"; }
fail() { printf '  FAIL %s\n' "$1" >&2; FAILURES=$((FAILURES + 1)); }
die() {
  fail "$1"
  printf '  HTTP %s\n  body: %s\n' "$HTTP_STATUS" "$BODY" >&2
  exit 1
}

sql() { "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "$1" 2>/dev/null; }

# req METHOD PATH [TOKEN] [JSON_BODY]
req() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-sS -X "$method" "$BASE$path" -w $'\n%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  local raw
  raw="$(curl "${args[@]}")" || die "curl $method $path failed"
  HTTP_STATUS="${raw##*$'\n'}"
  BODY="${raw%$'\n'*}"
}

# internal METHOD URL [JSON_BODY]
internal() {
  local method="$1" url="$2" data="${3:-}"
  local args=(-sS -X "$method" "$url" -H "X-Internal-Token: $INTERNAL_TOKEN" -w $'\n%{http_code}')
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  local raw
  raw="$(curl "${args[@]}")" || die "curl $method $url failed"
  HTTP_STATUS="${raw##*$'\n'}"
  BODY="${raw%$'\n'*}"
}

require_code() {
  local code
  code="$(jq -r '.code // empty' <<<"$BODY")"
  [ "$code" = "0" ] || die "$1 (code=$code)"
}

login() {
  local account="$1"
  req POST /api/v1/auth/login "" \
    "$(jq -nc --arg a "$account" --arg p "$PASSWORD" '{account:$a,password:$p}')"
  require_code "login $account"
  printf '  PASS login %s user=%s\n' "$account" \
    "$(jq -r '.data.user.nickname' <<<"$BODY")" >&2
  jq -r '.data.accessToken' <<<"$BODY"
}

stats_field() {
  # Runs in a subshell on purpose: only the value is needed by the caller.
  internal GET "$TRADE_INTERNAL/internal/v1/entitlement-outbox/stats"
  require_code "outbox stats"
  jq -r ".data.$1" <<<"$BODY"
}

restore_mock_flag() {
  printf '\n[cleanup] ensuring learn-service is running again\n'
  "${COMPOSE[@]}" up -d --no-deps learn-service >/dev/null 2>&1 || true
  if [ "$MOCK_TOGGLED" = "1" ]; then
    printf '[cleanup] recreating trade-service with the value from infra/.env\n'
    "${COMPOSE[@]}" up -d --no-deps --force-recreate trade-service >/dev/null 2>&1 || true
  fi
}
trap restore_mock_flag EXIT

wait_http() {
  local url="$1" attempts="$2"
  for _ in $(seq 1 "$attempts"); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" = "200" ] && return 0
    sleep 2
  done
  return 1
}

wait_outbox() {
  local where="$1" expected="$2" attempts="$3"
  local value=""
  for _ in $(seq 1 "$attempts"); do
    value="$(sql "SELECT status FROM lp_oms.entitlement_outbox WHERE $where")"
    [ "$value" = "$expected" ] && { printf '%s' "$value"; return 0; }
    sleep 2
  done
  printf '%s' "$value"
  return 1
}

printf 'Outbox fault drill run=%s target=%s\n' "$RUN_ID" "$BASE"

step 1 "prerequisites: stack health and mock payment channel"
wait_http "$BASE/actuator/health" 15 || die "gateway is not reachable"
pass "gateway healthy"
if grep -q '^LP_PAY_MOCK_ENABLED=true' "$ROOT/infra/.env" 2>/dev/null; then
  pass "mock payment already enabled"
else
  LP_PAY_MOCK_ENABLED=true "${COMPOSE[@]}" up -d --no-deps --force-recreate trade-service >/dev/null 2>&1 \
    || die "cannot recreate trade-service with mock payment enabled"
  MOCK_TOGGLED=1
  wait_http "$TRADE_INTERNAL/actuator/health" 30 || die "trade-service did not become healthy"
  pass "mock payment enabled for this run (infra/.env untouched)"
fi
sql "DELETE FROM lp_oms.entitlement_outbox WHERE order_no LIKE 'DRILL-%'" >/dev/null
pass "previous drill rows cleaned"
BASELINE_PENDING="$(stats_field pending)"
BASELINE_FAILED="$(stats_field failed)"
pass "gauge baseline pending=$BASELINE_PENDING failed=$BASELINE_FAILED"

step 2 "prepare a published course and resolve demo users"
TEACHER_TOKEN="$(login demo_teacher_lin)"
OPS_TOKEN="$(login demo_ops)"
STUDENT_TOKEN="$(login demo_buyer)"
BUYER_ID="$(sql "SELECT id FROM lp_ums.sys_user WHERE username='demo_buyer' LIMIT 1")"
[ -n "$BUYER_ID" ] || die "demo_buyer is missing; run db/seed/demo-seed.sql"
req GET /api/v1/categories
require_code "category list"
CATEGORY_ID="$(jq -r '.data[0].categoryId' <<<"$BODY")"
req POST /api/v1/teacher/courses "$TEACHER_TOKEN" \
  "$(jq -nc --argjson cat "$CATEGORY_ID" --arg title "$COURSE_TITLE" \
    '{categoryId:$cat,title:$title,subtitle:"outbox drill",coverUrl:"http://localhost:9000/lp-media/demo/cover.jpg",description:"outbox drill",price:19.90,originalPrice:29.90,level:1}')"
require_code "create course"
COURSE_ID="$(jq -r '.data.courseId' <<<"$BODY")"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters" "$TEACHER_TOKEN" \
  '{"title":"演练章节","sort":1,"status":1}'
require_code "create chapter"
CHAPTER_ID="$(jq -r '.data.chapterId' <<<"$BODY")"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters/$CHAPTER_ID/lessons" "$TEACHER_TOKEN" \
  '{"title":"演练课时","videoUrl":"demo/course-1.mp4","durationSec":300,"isFree":0,"sort":1,"status":1}'
require_code "create lesson"
req POST "/api/v1/teacher/courses/$COURSE_ID/submit" "$TEACHER_TOKEN"
require_code "submit course"
req POST "/api/v1/admin/courses/$COURSE_ID/review" "$OPS_TOKEN" \
  '{"approved":true,"remark":"outbox drill approved"}'
require_code "review course"
pass "courseId=$COURSE_ID published buyerId=$BUYER_ID"

step 3 "take learn-service down"
"${COMPOSE[@]}" stop learn-service >/dev/null 2>&1 || die "cannot stop learn-service"
[ "$(docker inspect -f '{{.State.Running}}' lp-learn-service)" = "false" ] || die "learn-service is still running"
pass "learn-service stopped"

step 4 "pay while learn-service is down: the order must still succeed"
req POST /api/v1/orders "$STUDENT_TOKEN" \
  "$(jq -nc --argjson cid "$COURSE_ID" --arg key "$RUN_KEY" \
    '{courseId:$cid,idempotencyKey:$key}')"
require_code "create order"
ORDER_NO="$(jq -r '.data.orderNo' <<<"$BODY")"
req POST "/api/v1/payments/$ORDER_NO/pay" "$STUDENT_TOKEN"
PAY_CODE="$(jq -r '.code // empty' <<<"$BODY")"
[ "$PAY_CODE" = "50205" ] && die "mock payment is disabled and could not be enabled"
require_code "start payment"
PAY_URL="$(jq -r '.data.payParams.payUrl' <<<"$BODY")"
req GET "${PAY_URL#"$BASE"}"
[ "$HTTP_STATUS" = "200" ] || die "payment callback did not return 200"
require_code "mock payment callback"
pass "callback HTTP 200 code 0"
ORDER_ID="$(sql "SELECT id FROM lp_oms.trade_order WHERE order_no='$ORDER_NO'")"
ORDER_STATUS="$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$ORDER_ID")"
[ "$ORDER_STATUS" = "1" ] || die "order $ORDER_NO is not paid (status=$ORDER_STATUS)"
pass "order status=$ORDER_STATUS (paid)"
req GET "/api/v1/orders/$ORDER_ID" "$STUDENT_TOKEN"
require_code "order detail"
DETAIL_STATUS="$(jq -r '.data.status' <<<"$BODY")"
[ "$DETAIL_STATUS" = "1" ] || die "order detail does not show paid (status=$DETAIL_STATUS)"
pass "order detail status=$DETAIL_STATUS"
OUTBOX_ROW="$(sql "SELECT CONCAT_WS('|',id,status,retry_count,next_retry_at,IFNULL(delivered_at,'')) FROM lp_oms.entitlement_outbox WHERE order_id=$ORDER_ID")"
[ -n "$OUTBOX_ROW" ] || die "no outbox row for order $ORDER_ID"
pass "outbox row id|status|retry|next_retry|delivered = $OUTBOX_ROW"

step 5 "relay retries with backoff and does not give up"
OUTBOX_ID="${OUTBOX_ROW%%|*}"
RETRY=0
for _ in $(seq 1 25); do
  RETRY="$(sql "SELECT retry_count FROM lp_oms.entitlement_outbox WHERE id=$OUTBOX_ID")"
  [ "${RETRY:-0}" -ge 1 ] && break
  sleep 2
done
[ "${RETRY:-0}" -ge 1 ] || die "retry_count never advanced"
STATUS_NOW="$(sql "SELECT status FROM lp_oms.entitlement_outbox WHERE id=$OUTBOX_ID")"
BACKOFF_OK="$(sql "SELECT next_retry_at > NOW(3) FROM lp_oms.entitlement_outbox WHERE id=$OUTBOX_ID")"
[ "$STATUS_NOW" = "0" ] || die "row left pending too early (status=$STATUS_NOW)"
[ "$BACKOFF_OK" = "1" ] || die "next_retry_at is not in the future"
LAST_ERROR="$(sql "SELECT IFNULL(last_error,'') FROM lp_oms.entitlement_outbox WHERE id=$OUTBOX_ID")"
pass "status=$STATUS_NOW retry_count=$RETRY next_retry_at in future last_error=${LAST_ERROR:0:60}"

PENDING_GAUGE=0
for _ in $(seq 1 20); do
  PENDING_GAUGE="$(stats_field pending)"
  [ "${PENDING_GAUGE:-0}" -gt "${BASELINE_PENDING:-0}" ] && break
  sleep 2
done
[ "${PENDING_GAUGE:-0}" -gt "${BASELINE_PENDING:-0}" ] || die "pending gauge did not increase"
pass "gauge entitlement_outbox_pending=$PENDING_GAUGE (baseline $BASELINE_PENDING)"

FAILED_NOW="$(stats_field failed)"
[ "${FAILED_NOW:-1}" = "$BASELINE_FAILED" ] || die "row was marked failed before the retry limit (failed=$FAILED_NOW)"
pass "gauge entitlement_outbox_failed=$FAILED_NOW (unchanged)"

step 6 "recover learn-service: the relay delivers automatically"
"${COMPOSE[@]}" up -d --wait --no-deps learn-service >/dev/null 2>&1 || die "cannot start learn-service"
wait_http "$LEARN_INTERNAL/actuator/health" 30 || die "learn-service did not become healthy"
pass "learn-service healthy"

if wait_outbox "id=$OUTBOX_ID" 1 60 >/dev/null; then
  pass "outbox row delivered"
else
  die "outbox row was not delivered within 120s"
fi
DELIVERED_AT="$(sql "SELECT IFNULL(delivered_at,'') FROM lp_oms.entitlement_outbox WHERE id=$OUTBOX_ID")"
[ -n "$DELIVERED_AT" ] || die "delivered_at is empty"
ENTITLEMENTS="$(sql "SELECT COUNT(*) FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID")"
[ "$ENTITLEMENTS" = "1" ] || die "expected exactly one entitlement, got $ENTITLEMENTS"
pass "delivered_at=$DELIVERED_AT entitlement rows=$ENTITLEMENTS"

req GET "/api/v1/learning/my-courses?pageNum=1&pageSize=50" "$STUDENT_TOKEN"
require_code "my courses"
OWNED="$(jq -r --argjson cid "$COURSE_ID" '[.data.records[] | select(.courseId == $cid)] | length' <<<"$BODY")"
[ "$OWNED" = "1" ] || die "course $COURSE_ID is missing from my-courses"
pass "my-courses contains courseId=$COURSE_ID"

for _ in $(seq 1 20); do
  PENDING_GAUGE="$(stats_field pending)"
  [ "${PENDING_GAUGE:-1}" -le "${BASELINE_PENDING:-0}" ] && break
  sleep 2
done
[ "${PENDING_GAUGE:-1}" -le "${BASELINE_PENDING:-0}" ] || die "pending gauge did not return to the baseline"
pass "gauge entitlement_outbox_pending=$PENDING_GAUGE (baseline $BASELINE_PENDING)"

step 7 "idempotency: replaying the same grant adds no row"
for attempt in 1 2 3; do
  internal POST "$LEARN_INTERNAL/internal/v1/entitlements" \
    "$(jq -nc --argjson uid "$BUYER_ID" --argjson cid "$COURSE_ID" --argjson oid "$ORDER_ID" \
      '{userId:$uid,courseId:$cid,orderId:$oid,source:1}')"
  require_code "replay grant #$attempt"
done
COUNT_AFTER="$(sql "SELECT COUNT(*) FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID")"
[ "$COUNT_AFTER" = "1" ] || die "replay created $COUNT_AFTER entitlement rows"
pass "3 replays accepted, entitlement rows=$COUNT_AFTER"

step 8 "dead letter: a row past the retry limit is given up and visible"
"${COMPOSE[@]}" stop learn-service >/dev/null 2>&1 || die "cannot stop learn-service"
DEAD_ORDER_ID=$((900000000 + RUN_ID % 100000000))
DEAD_ORDER_NO="DRILL-DEAD-$RUN_ID"
sql "INSERT INTO lp_oms.entitlement_outbox (order_id, order_no, user_id, course_id, status, retry_count, next_retry_at) VALUES ($DEAD_ORDER_ID, '$DEAD_ORDER_NO', $BUYER_ID, $COURSE_ID, 0, 9, NOW(3))" >/dev/null \
  || die "cannot insert synthetic outbox row"
DEAD_ID="$(sql "SELECT id FROM lp_oms.entitlement_outbox WHERE order_no='$DEAD_ORDER_NO'")"
[ -n "$DEAD_ID" ] || die "synthetic row is missing"

if wait_outbox "id=$DEAD_ID" 2 30 >/dev/null; then
  pass "row $DEAD_ID moved to failed (status=2)"
else
  die "row $DEAD_ID did not reach the failed state"
fi
DEAD_RETRY="$(sql "SELECT retry_count FROM lp_oms.entitlement_outbox WHERE id=$DEAD_ID")"
DEAD_ERROR="$(sql "SELECT IFNULL(last_error,'') FROM lp_oms.entitlement_outbox WHERE id=$DEAD_ID")"
[ "${DEAD_RETRY:-0}" -ge 10 ] || die "failed row retry_count=$DEAD_RETRY is below the limit"
[ -n "$DEAD_ERROR" ] || die "failed row has no last_error"
pass "retry_count=$DEAD_RETRY last_error=${DEAD_ERROR:0:60}"

internal GET "$TRADE_INTERNAL/internal/v1/entitlement-outbox/failed?pageNum=1&pageSize=50"
require_code "failed list"
IN_FAILED="$(jq -r --argjson id "$DEAD_ID" '[.data.records[] | select(.id == $id)] | length' <<<"$BODY")"
[ "$IN_FAILED" = "1" ] || die "failed API does not list row $DEAD_ID"
pass "failed API lists row $DEAD_ID"

FAILED_GAUGE=0
for _ in $(seq 1 20); do
  FAILED_GAUGE="$(stats_field failed)"
  [ "${FAILED_GAUGE:-0}" -gt "${BASELINE_FAILED:-0}" ] && break
  sleep 2
done
[ "${FAILED_GAUGE:-0}" -gt "${BASELINE_FAILED:-0}" ] || die "failed gauge did not increase"
pass "gauge entitlement_outbox_failed=$FAILED_GAUGE (baseline $BASELINE_FAILED)"

step 9 "operations requeues the dead row"
"${COMPOSE[@]}" up -d --wait --no-deps learn-service >/dev/null 2>&1 || die "cannot start learn-service"
wait_http "$LEARN_INTERNAL/actuator/health" 30 || die "learn-service did not become healthy"
internal POST "$TRADE_INTERNAL/internal/v1/entitlement-outbox/$DEAD_ID/retry"
require_code "retry failed row"
[ "$(jq -r '.data.requeued' <<<"$BODY")" = "true" ] || die "retry response is not requeued=true"
pass "retry accepted"

if wait_outbox "id=$DEAD_ID" 1 60 >/dev/null; then
  pass "requeued row delivered"
else
  die "requeued row was not delivered within 120s"
fi

FAILED_GAUGE=$((BASELINE_FAILED + 1))
for _ in $(seq 1 20); do
  FAILED_GAUGE="$(stats_field failed)"
  [ "${FAILED_GAUGE:-1}" -le "${BASELINE_FAILED:-0}" ] && break
  sleep 2
done
[ "${FAILED_GAUGE:-1}" -le "${BASELINE_FAILED:-0}" ] || die "failed gauge did not return to the baseline"
pass "gauge entitlement_outbox_failed=$FAILED_GAUGE (baseline $BASELINE_FAILED)"

printf '\nDRILL OK: order stayed paid during the outage, retries backed off, delivery recovered, replay was idempotent, dead letter was requeued.\n'
printf 'Artifacts kept for inspection: order=%s course=%s outbox rows=%s,%s\n' \
  "$ORDER_NO" "$COURSE_ID" "$OUTBOX_ID" "$DEAD_ID"
exit 0
