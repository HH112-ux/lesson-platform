#!/usr/bin/env bash
#
# Refund loop drill.
#
# Proves the whole buyer refund path against the local stack instead of only in unit tests:
#   1. buy and pay a course, entitlement is granted and the lesson plays;
#   2. applying only creates a pending row: the order stays paid, no channel refund runs;
#   3. operations approval calls the mock channel: the refund row, the order status and the
#      revoke outbox row appear;
#   4. the relay delivers the revoke: the entitlement is revoked, not deleted, progress stays;
#   5. my-courses marks the course as refunded and the play token is rejected with 40302;
#   6. re-applying after the refund replays the original refund and adds no row;
#   7. applying eight days after payment is rejected with 62007 and creates no row;
#   8. a rejection requires a reason, keeps the order paid and allows a new application.
#
# Usage:
#   bash scripts/refund-drill.sh
#
# Requires the local stack (scripts/dev-up.sh) and jq. The script temporarily recreates
# trade-service with LP_PAY_MOCK_ENABLED=true and a development mock secret, then restores it on
# exit. Every step prints the observed values; the script exits non-zero on the first failed
# assertion. Each run creates its own courses and orders, so it can be run repeatedly.
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
IDEMPOTENCY_SEQ=0
COURSE_TITLE="退款闭环演练 $RUN_ID"
SECOND_COURSE_TITLE="退款超期演练 $RUN_ID"
THIRD_COURSE_TITLE="退款驳回演练 $RUN_ID"
FIXTURE="$ROOT/scripts/fixtures/sample.mp4"

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
  # Payment initiation and refund application both enforce the idempotency header
  # contract. A per-call sequence keeps them distinct, including the re-apply after a
  # rejection that shares its order number with the rejected application.
  case "$path" in
    */pay)
      IDEMPOTENCY_SEQ=$((IDEMPOTENCY_SEQ + 1))
      args+=(-H "Idempotency-Key: pay-$RUN_ID-$IDEMPOTENCY_SEQ")
      ;;
    */refund)
      IDEMPOTENCY_SEQ=$((IDEMPOTENCY_SEQ + 1))
      args+=(-H "Idempotency-Key: refund-$RUN_ID-$IDEMPOTENCY_SEQ")
      ;;
  esac
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  local raw
  raw="$(curl "${args[@]}")" || die "curl $method $path failed"
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
  printf '  PASS login %s\n' "$account" >&2
  jq -r '.data.accessToken' <<<"$BODY"
}

restore_mock_flag() {
  if [ "$MOCK_TOGGLED" = "1" ]; then
    printf '\n[cleanup] recreating trade-service with the value from infra/.env\n'
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

wait_sql() {
  local query="$1" expected="$2" attempts="$3"
  local value=""
  for _ in $(seq 1 "$attempts"); do
    value="$(sql "$query")"
    [ "$value" = "$expected" ] && { printf '%s' "$value"; return 0; }
    sleep 2
  done
  printf '%s' "$value"
  return 1
}

# Uploads the fixture to a presigned PUT URL. The credential is signed for the compose host
# (minio:9000), which the host itself cannot resolve, so curl connects to the published loopback
# port while keeping the signed Host header.
put_fixture() {
  local url="$1" authority host port status
  authority="${url#http://}"
  authority="${authority%%/*}"
  host="${authority%%:*}"
  port="${authority##*:}"
  [ "$port" = "$authority" ] && port=80
  status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT \
    --resolve "$host:$port:127.0.0.1" \
    -H 'Content-Type: video/mp4' --data-binary "@$FIXTURE" "$url")" \
    || die "upload PUT failed"
  [ "$status" = "200" ] || die "upload PUT returned HTTP $status"
}

publish_course() {
  local title="$1" price="$2"
  req POST /api/v1/teacher/courses "$TEACHER_TOKEN" \
    "$(jq -nc --argjson cat "$CATEGORY_ID" --arg title "$title" --argjson price "$price" \
      '{categoryId:$cat,title:$title,subtitle:"refund drill",coverUrl:"http://localhost:9000/lp-media/demo/cover.jpg",description:"refund drill",price:$price,originalPrice:($price+10),level:1}')"
  require_code "create course $title"
  local course_id
  course_id="$(jq -r '.data.courseId' <<<"$BODY")"
  req POST "/api/v1/teacher/courses/$course_id/chapters" "$TEACHER_TOKEN" \
    '{"title":"演练章节","sort":1,"status":1}'
  require_code "create chapter"
  local chapter_id
  chapter_id="$(jq -r '.data.chapterId' <<<"$BODY")"
  req POST "/api/v1/teacher/courses/$course_id/chapters/$chapter_id/lessons" "$TEACHER_TOKEN" \
    '{"title":"演练课时","isFree":0,"sort":1,"status":1}'
  require_code "create lesson"
  local lesson_id
  lesson_id="$(jq -r '.data.lessonId' <<<"$BODY")"
  local fixture_size
  fixture_size="$(wc -c < "$FIXTURE" | tr -d '[:space:]')"
  req POST "/api/v1/teacher/courses/$course_id/chapters/$chapter_id/lessons/$lesson_id/upload-sessions" \
    "$TEACHER_TOKEN" "$(jq -nc --argjson size "$fixture_size" '{expectedSizeBytes:$size}')"
  require_code "open upload session"
  local upload_id
  upload_id="$(jq -r '.data.uploadId' <<<"$BODY")"
  put_fixture "$(jq -r '.data.uploadUrl' <<<"$BODY")"
  req POST "/api/v1/teacher/courses/$course_id/chapters/$chapter_id/lessons/$lesson_id/upload-sessions/$upload_id/confirm" \
    "$TEACHER_TOKEN"
  require_code "confirm upload"
  req POST "/api/v1/teacher/courses/$course_id/submit" "$TEACHER_TOKEN"
  require_code "submit course"
  req POST "/api/v1/admin/courses/$course_id/review" "$OPS_TOKEN" \
    '{"approved":true,"remark":"refund drill approved"}'
  require_code "review course"
  printf '%s|%s' "$course_id" "$lesson_id"
}

buy_course() {
  local course_id="$1" key="$2"
  req POST /api/v1/orders "$STUDENT_TOKEN" \
    "$(jq -nc --argjson cid "$course_id" --arg key "$key" '{courseId:$cid,idempotencyKey:$key}')"
  require_code "create order"
  local order_no
  order_no="$(jq -r '.data.orderNo' <<<"$BODY")"
  req POST "/api/v1/payments/$order_no/pay" "$STUDENT_TOKEN"
  local pay_code
  pay_code="$(jq -r '.code // empty' <<<"$BODY")"
  [ "$pay_code" = "50205" ] && die "mock payment is disabled and could not be enabled"
  require_code "start payment"
  local pay_url
  pay_url="$(jq -r '.data.payParams.payUrl' <<<"$BODY")"
  req GET "${pay_url#"$BASE"}"
  [ "$HTTP_STATUS" = "200" ] || die "mock payment callback did not return 200"
  require_code "mock payment callback"
  printf '%s' "$order_no"
}

printf 'Refund drill run=%s target=%s\n' "$RUN_ID" "$BASE"

step 1 "prerequisites: stack health and mock payment channel"
wait_http "$BASE/actuator/health" 15 || die "gateway is not reachable"
pass "gateway healthy"
if grep -q '^LP_PAY_MOCK_ENABLED=true' "$ROOT/infra/.env" 2>/dev/null; then
  pass "mock payment already enabled"
else
  LP_PAY_MOCK_ENABLED=true \
    LP_PAY_MOCK_SECRET="${LP_PAY_MOCK_SECRET:-lp_mock_drill_secret}" \
    "${COMPOSE[@]}" up -d --no-deps --force-recreate trade-service >/dev/null 2>&1 \
    || die "cannot recreate trade-service with mock payment enabled"
  MOCK_TOGGLED=1
  wait_http "$TRADE_INTERNAL/actuator/health" 30 || die "trade-service did not become healthy"
  pass "mock payment enabled for this run (infra/.env untouched)"
fi

step 2 "create and publish the drill course"
TEACHER_TOKEN="$(login demo_teacher_lin)"
OPS_TOKEN="$(login demo_ops)"
STUDENT_TOKEN="$(login demo_buyer)"
BUYER_ID="$(sql "SELECT id FROM lp_ums.sys_user WHERE username='demo_buyer' LIMIT 1")"
[ -n "$BUYER_ID" ] || die "demo_buyer is missing; run db/seed/demo-seed.sql"
req GET /api/v1/categories
require_code "category list"
CATEGORY_ID="$(jq -r '.data[0].categoryId' <<<"$BODY")"
COURSE_PAIR="$(publish_course "$COURSE_TITLE" 19.90)"
COURSE_ID="${COURSE_PAIR%%|*}"
LESSON_ID="${COURSE_PAIR##*|}"
[ -n "$COURSE_ID" ] && [ -n "$LESSON_ID" ] || die "cannot resolve the drill course"
pass "courseId=$COURSE_ID lessonId=$LESSON_ID buyerId=$BUYER_ID"

step 3 "buy, pay and wait for the entitlement"
ORDER_NO="$(buy_course "$COURSE_ID" "refund-drill-$RUN_ID")"
ORDER_ID="$(sql "SELECT id FROM lp_oms.trade_order WHERE order_no='$ORDER_NO'")"
[ -n "$ORDER_ID" ] || die "order $ORDER_NO is missing"
PAID_AMOUNT="$(sql "SELECT pay_amount FROM lp_oms.trade_order WHERE id=$ORDER_ID")"
[ "$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$ORDER_ID")" = "1" ] \
  || die "order $ORDER_NO is not paid"
pass "orderId=$ORDER_ID orderNo=$ORDER_NO paid=$PAID_AMOUNT"

ENTITLED="$(wait_sql "SELECT status FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID" 0 30)"
[ "$ENTITLED" = "0" ] || die "entitlement was not granted (status=$ENTITLED)"
GRANTED_AT="$(sql "SELECT IFNULL(granted_at,'') FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID")"
pass "entitlement active since $GRANTED_AT"

req GET "/api/v1/lessons/$LESSON_ID/play-token" "$STUDENT_TOKEN"
require_code "play token before refund"
pass "play token issued before refund"

step 4 "apply for the refund: a pending row only, no channel call"
req POST "/api/v1/orders/$ORDER_ID/refund" "$STUDENT_TOKEN" \
  '{"reason":"refund drill"}'
require_code "apply refund"
REFUND_ID="$(jq -r '.data.refundId' <<<"$BODY")"
REFUND_NO="$(jq -r '.data.refundNo' <<<"$BODY")"
REFUND_STATUS="$(jq -r '.data.status' <<<"$BODY")"
[ "$REFUND_STATUS" = "0" ] || die "refund is not pending review (status=$REFUND_STATUS)"
[ "$REFUND_NO" = "RF$ORDER_NO" ] || die "unexpected refund number $REFUND_NO"
[ -n "$REFUND_ID" ] && [ "$REFUND_ID" != "null" ] || die "response has no refundId"
[ "$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$ORDER_ID")" = "1" ] \
  || die "application changed the order status before review"
[ "$(sql "SELECT COUNT(*) FROM lp_oms.entitlement_outbox WHERE order_id=$ORDER_ID AND action=1")" = "0" ] \
  || die "application revoked the entitlement before review"
pass "refundId=$REFUND_ID refundNo=$REFUND_NO status=0, order still paid, entitlement kept"

step 5 "operations approval: channel refund, order refunded, revoke outbox"
req POST "/api/v1/admin/refunds/$REFUND_ID/review" "$OPS_TOKEN" '{"approved":true}'
require_code "approve refund"
APPROVED_STATUS="$(jq -r '.data.status' <<<"$BODY")"
[ "$APPROVED_STATUS" = "2" ] || die "approval did not refund (status=$APPROVED_STATUS)"

[ "$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$ORDER_ID")" = "5" ] \
  || die "order $ORDER_NO is not refunded"
pass "order status=5 (refunded)"

REFUND_ROW="$(sql "SELECT CONCAT_WS('|',status,amount,IFNULL(channel_refund_no,'')) FROM lp_oms.refund_request WHERE id=$REFUND_ID")"
[ "${REFUND_ROW%%|*}" = "2" ] || die "refund row is not refunded ($REFUND_ROW)"
pass "refund_request status|amount|channel_refund_no = $REFUND_ROW"

REVOKE_STATUS="$(wait_sql "SELECT status FROM lp_oms.entitlement_outbox WHERE order_id=$ORDER_ID AND action=1" 1 60)"
[ "$REVOKE_STATUS" = "1" ] || die "revoke outbox row was not delivered (status=$REVOKE_STATUS)"
pass "revoke outbox row delivered"

step 6 "after the revoke: entitlement revoked, course marked, playback denied"
ENTITLEMENT_ROW="$(sql "SELECT CONCAT_WS('|',status,user_id,course_id,IFNULL(order_id,''),IFNULL(revoked_at,'')) FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID")"
[ -n "$ENTITLEMENT_ROW" ] || die "entitlement row was deleted instead of revoked"
[ "${ENTITLEMENT_ROW%%|*}" = "1" ] || die "entitlement is not revoked ($ENTITLEMENT_ROW)"
FIRST_REVOKED_AT="${ENTITLEMENT_ROW##*|}"
[ -n "$FIRST_REVOKED_AT" ] || die "revoked_at is empty"
pass "entitlement status|user|course|order|revoked_at = $ENTITLEMENT_ROW"

req GET "/api/v1/learning/my-courses?pageNum=1&pageSize=50" "$STUDENT_TOKEN"
require_code "my courses after refund"
REFUNDED="$(jq -r --argjson cid "$COURSE_ID" \
  '[.data.records[] | select(.courseId == $cid and .refunded == true)] | length' <<<"$BODY")"
[ "$REFUNDED" = "1" ] || die "course $COURSE_ID is not marked as refunded in my-courses"
pass "my-courses refunded=true"

req GET "/api/v1/lessons/$LESSON_ID/play-token" "$STUDENT_TOKEN"
PLAY_CODE="$(jq -r '.code // empty' <<<"$BODY")"
[ "$PLAY_CODE" = "40302" ] || die "play token after refund returned code=$PLAY_CODE instead of 40302"
pass "play token rejected with 40302"

step 7 "re-applying after the refund replays the original"
req POST "/api/v1/orders/$ORDER_ID/refund" "$STUDENT_TOKEN" \
  '{"reason":"refund drill again"}'
require_code "re-apply after refund"
REPLAY_NO="$(jq -r '.data.refundNo' <<<"$BODY")"
[ "$REPLAY_NO" = "$REFUND_NO" ] || die "re-apply returned $REPLAY_NO instead of $REFUND_NO"
REPLAY_STATUS="$(jq -r '.data.status' <<<"$BODY")"
[ "$REPLAY_STATUS" = "2" ] || die "re-apply returned status=$REPLAY_STATUS instead of 2"
REFUND_ROWS="$(sql "SELECT COUNT(*) FROM lp_oms.refund_request WHERE order_id=$ORDER_ID")"
[ "$REFUND_ROWS" = "1" ] || die "re-apply created $REFUND_ROWS refund rows"
REVOKED_AT="$(sql "SELECT IFNULL(revoked_at,'') FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$COURSE_ID")"
[ "$REVOKED_AT" = "$FIRST_REVOKED_AT" ] || die "re-apply changed revoked_at from $FIRST_REVOKED_AT to $REVOKED_AT"
REVOKE_ROWS="$(sql "SELECT COUNT(*) FROM lp_oms.entitlement_outbox WHERE order_id=$ORDER_ID AND action=1")"
[ "$REVOKE_ROWS" = "1" ] || die "re-apply created $REVOKE_ROWS revoke outbox rows"
pass "re-apply replayed $REFUND_NO status=2, refund rows=1, revoke rows=1, revoked_at unchanged"

step 8 "over seven days: admission is rejected and no row is created"
SECOND_PAIR="$(publish_course "$SECOND_COURSE_TITLE" 29.90)"
SECOND_COURSE_ID="${SECOND_PAIR%%|*}"
SECOND_ORDER_NO="$(buy_course "$SECOND_COURSE_ID" "refund-drill-late-$RUN_ID")"
SECOND_ORDER_ID="$(sql "SELECT id FROM lp_oms.trade_order WHERE order_no='$SECOND_ORDER_NO'")"
SECOND_PAID="$(sql "SELECT pay_amount FROM lp_oms.trade_order WHERE id=$SECOND_ORDER_ID")"
sql "UPDATE lp_oms.trade_order SET paid_at = DATE_SUB(NOW(), INTERVAL 8 DAY) WHERE id=$SECOND_ORDER_ID" >/dev/null
req POST "/api/v1/orders/$SECOND_ORDER_ID/refund" "$STUDENT_TOKEN" \
  '{"reason":"late refund"}'
LATE_CODE="$(jq -r '.code // empty' <<<"$BODY")"
[ "$LATE_CODE" = "62007" ] || die "late refund returned code=$LATE_CODE instead of 62007"
SECOND_REFUNDS="$(sql "SELECT COUNT(*) FROM lp_oms.refund_request WHERE order_id=$SECOND_ORDER_ID")"
[ "$SECOND_REFUNDS" = "0" ] || die "late refund created $SECOND_REFUNDS rows"
[ "$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$SECOND_ORDER_ID")" = "1" ] \
  || die "late refund changed the order status"
pass "paid=$SECOND_PAID paid_at 8 days ago rejected with 62007, nothing created"

step 9 "rejection: reason required, order stays paid, a new application is admitted"
THIRD_PAIR="$(publish_course "$THIRD_COURSE_TITLE" 39.90)"
THIRD_COURSE_ID="${THIRD_PAIR%%|*}"
THIRD_ORDER_NO="$(buy_course "$THIRD_COURSE_ID" "refund-drill-reject-$RUN_ID")"
THIRD_ORDER_ID="$(sql "SELECT id FROM lp_oms.trade_order WHERE order_no='$THIRD_ORDER_NO'")"
THIRD_ENTITLEMENT="$(wait_sql "SELECT status FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$THIRD_COURSE_ID" 0 60)"
[ "$THIRD_ENTITLEMENT" = "0" ] || die "entitlement of order $THIRD_ORDER_NO is not active (status=$THIRD_ENTITLEMENT)"
req POST "/api/v1/orders/$THIRD_ORDER_ID/refund" "$STUDENT_TOKEN" \
  '{"reason":"rejection drill"}'
require_code "apply before rejection"
THIRD_REFUND_ID="$(jq -r '.data.refundId' <<<"$BODY")"

req POST "/api/v1/admin/refunds/$THIRD_REFUND_ID/review" "$OPS_TOKEN" '{"approved":false}'
NO_REASON_CODE="$(jq -r '.code // empty' <<<"$BODY")"
[ "$NO_REASON_CODE" = "40001" ] || die "rejection without reason returned code=$NO_REASON_CODE instead of 40001"

req POST "/api/v1/admin/refunds/$THIRD_REFUND_ID/review" "$OPS_TOKEN" \
  '{"approved":false,"rejectReason":"不符合退款条件"}'
require_code "reject refund"
REJECTED_STATUS="$(jq -r '.data.status' <<<"$BODY")"
[ "$REJECTED_STATUS" = "3" ] || die "rejection status=$REJECTED_STATUS instead of 3"
[ "$(sql "SELECT status FROM lp_oms.trade_order WHERE id=$THIRD_ORDER_ID")" = "1" ] \
  || die "rejection changed the order status"
[ "$(sql "SELECT status FROM lp_lms.user_entitlement WHERE user_id=$BUYER_ID AND course_id=$THIRD_COURSE_ID")" = "0" ] \
  || die "rejection revoked the entitlement"
pass "rejection requires a reason, keeps the order paid and the entitlement active"

req POST "/api/v1/orders/$THIRD_ORDER_ID/refund" "$STUDENT_TOKEN" \
  '{"reason":"reapply after rejection"}'
require_code "re-apply after rejection"
SECOND_REFUND_NO="$(jq -r '.data.refundNo' <<<"$BODY")"
[ "$SECOND_REFUND_NO" = "RF$THIRD_ORDER_NO-2" ] || die "re-apply refund number is $SECOND_REFUND_NO"
pass "re-apply admitted with refundNo=$SECOND_REFUND_NO"

printf '\nREFUND OK: application only queues, approval refunds, entitlement revoked without deleting rows, playback denied, late and post-refund applications rejected, rejection allows re-apply.\n'
printf 'Artifacts kept for inspection: order=%s course=%s lesson=%s lateOrder=%s rejectedOrder=%s\n' \
  "$ORDER_NO" "$COURSE_ID" "$LESSON_ID" "$SECOND_ORDER_NO" "$THIRD_ORDER_NO"
exit 0
