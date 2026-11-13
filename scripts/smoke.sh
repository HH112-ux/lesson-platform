#!/usr/bin/env bash
#
# Runs the whole MVP chain through the gateway: teacher creates a course, submits it,
# operations approves it, a student buys it, the local mock channel completes the
# payment (still signature verified), ownership appears and a playback URL is issued.
#
# Usage: bash scripts/smoke.sh
#
# Every step prints the HTTP status plus the field it asserts on. The script stops at
# the first failure and prints the response body. Any skipped step is counted and makes
# the run fail: CI must not report green when part of the chain never ran.
set -uo pipefail

BASE="${LP_SMOKE_BASE:-http://localhost:8080}"
PASSWORD="${LP_SMOKE_PASSWORD:-Demo@123456}"
COURSE_TITLE="冒烟验证课程 $(date +%H%M%S)"
RUN_KEY="smoke-$(date +%Y%m%d%H%M%S)"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT/scripts/fixtures/sample.mp4"

HTTP_STATUS=""
BODY=""
SKIPPED_COUNT=0

command -v jq >/dev/null 2>&1 || { echo "FAIL jq is required"; exit 1; }

step() { printf '\n[%s] %s\n' "$1" "$2"; }
pass() { printf '  HTTP %s | %s\n' "$HTTP_STATUS" "$1"; }
die() {
  printf 'FAIL %s\n' "$1" >&2
  printf '  HTTP %s\n  body: %s\n' "$HTTP_STATUS" "$BODY" >&2
  exit 1
}

# req METHOD PATH [TOKEN] [JSON_BODY]
req() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-sS -X "$method" "$BASE$path" -w $'\n%{http_code}')
  if [ -n "$token" ]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  # Payment initiation enforces the idempotency header contract; without it the
  # request is rejected with 40001 before any channel logic runs.
  case "$path" in
    */pay) args+=(-H "Idempotency-Key: pay-$RUN_KEY") ;;
  esac
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' -d "$data")
  fi
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
  printf '  HTTP %s | fixture uploaded\n' "$status"
}

login() {
  local account="$1"
  req POST /api/v1/auth/login "" \
    "$(jq -nc --arg a "$account" --arg p "$PASSWORD" '{account:$a,password:$p}')"
  require_code "login $account"
  # Progress goes to stderr so command substitution only captures the token.
  printf '  HTTP %s | login ok user=%s\n' "$HTTP_STATUS" \
    "$(jq -r '.data.user.nickname' <<<"$BODY")" >&2
  jq -r '.data.accessToken' <<<"$BODY"
}

printf 'Smoke target: %s\n' "$BASE"

step 1 "gateway health"
req GET /actuator/health
[ "$HTTP_STATUS" = "200" ] || die "gateway is not reachable"
pass "status=$(jq -r '.data.status // .status // "unknown"' <<<"$BODY" 2>/dev/null || echo unknown)"

step 2 "teacher login"
TEACHER_TOKEN="$(login demo_teacher_lin)"

step 3 "teacher creates a draft course"
req GET /api/v1/categories
require_code "category list"
CATEGORY_ID="$(jq -r '.data[0].categoryId' <<<"$BODY")"
pass "categoryId=$CATEGORY_ID"
req POST /api/v1/teacher/courses "$TEACHER_TOKEN" \
  "$(jq -nc --argjson cat "$CATEGORY_ID" --arg title "$COURSE_TITLE" \
    '{categoryId:$cat,title:$title,subtitle:"smoke",coverUrl:"http://localhost:9000/lp-media/demo/cover.jpg",description:"smoke course",price:19.90,originalPrice:29.90,level:1}')"
require_code "create course"
COURSE_ID="$(jq -r '.data.courseId' <<<"$BODY")"
pass "courseId=$COURSE_ID status=$(jq -r '.data.status' <<<"$BODY")"

step 4 "teacher adds a chapter and a lesson with an uploaded video"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters" "$TEACHER_TOKEN" \
  '{"title":"冒烟章节","sort":1,"status":1}'
require_code "create chapter"
CHAPTER_ID="$(jq -r '.data.chapterId' <<<"$BODY")"
pass "chapterId=$CHAPTER_ID"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters/$CHAPTER_ID/lessons" "$TEACHER_TOKEN" \
  '{"title":"冒烟课时","isFree":0,"sort":1,"status":1}'
require_code "create lesson"
LESSON_ID="$(jq -r '.data.lessonId' <<<"$BODY")"
pass "lessonId=$LESSON_ID"
FIXTURE_SIZE="$(wc -c < "$FIXTURE" | tr -d '[:space:]')"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters/$CHAPTER_ID/lessons/$LESSON_ID/upload-sessions" \
  "$TEACHER_TOKEN" "$(jq -nc --argjson size "$FIXTURE_SIZE" '{expectedSizeBytes:$size}')"
require_code "open upload session"
UPLOAD_ID="$(jq -r '.data.uploadId' <<<"$BODY")"
UPLOAD_URL="$(jq -r '.data.uploadUrl' <<<"$BODY")"
pass "uploadId=$UPLOAD_ID expectedSizeBytes=$FIXTURE_SIZE"
put_fixture "$UPLOAD_URL"
req POST "/api/v1/teacher/courses/$COURSE_ID/chapters/$CHAPTER_ID/lessons/$LESSON_ID/upload-sessions/$UPLOAD_ID/confirm" \
  "$TEACHER_TOKEN"
require_code "confirm upload"
pass "durationSec=$(jq -r '.data.durationSec' <<<"$BODY") codec=$(jq -r '.data.videoCodec' <<<"$BODY")"

step 5 "teacher submits the course for review"
req POST "/api/v1/teacher/courses/$COURSE_ID/submit" "$TEACHER_TOKEN"
require_code "submit course"
pass "status=$(jq -r '.data.status' <<<"$BODY")"

step 6 "operations approves and publishes the course"
OPS_TOKEN="$(login demo_ops)"
req POST "/api/v1/admin/courses/$COURSE_ID/review" "$OPS_TOKEN" \
  '{"approved":true,"remark":"smoke approved"}'
require_code "review course"
pass "status=$(jq -r '.data.status' <<<"$BODY")"

step 7 "student browses the published course"
STUDENT_TOKEN="$(login demo_buyer)"
req GET "/api/v1/courses/$COURSE_ID"
require_code "course detail"
pass "title=$(jq -r '.data.title' <<<"$BODY") price=$(jq -r '.data.price' <<<"$BODY")"

step 8 "student creates an order"
req POST /api/v1/orders "$STUDENT_TOKEN" \
  "$(jq -nc --argjson cid "$COURSE_ID" --arg key "$RUN_KEY" \
    '{courseId:$cid,idempotencyKey:$key}')"
require_code "create order"
ORDER_NO="$(jq -r '.data.orderNo' <<<"$BODY")"
pass "orderNo=$ORDER_NO payAmount=$(jq -r '.data.payAmount' <<<"$BODY")"

step 9 "student starts the payment"
req POST "/api/v1/payments/$ORDER_NO/pay" "$STUDENT_TOKEN"
PAY_CODE="$(jq -r '.code // empty' <<<"$BODY")"
if [ "$PAY_CODE" = "50205" ]; then
  SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
  printf '  HTTP %s | SKIPPED mock payment channel is disabled (lp.pay.mock.enabled=false)\n' "$HTTP_STATUS"
  printf '  remaining payment, ownership, playback and progress steps SKIPPED\n'
  printf '\nSMOKE FAIL: %s step skipped; set LP_PAY_MOCK_ENABLED=true and rerun\n' "$SKIPPED_COUNT"
  exit 1
fi
require_code "start payment"
PAY_URL="$(jq -r '.data.payParams.payUrl' <<<"$BODY")"
PAY_NO="$(jq -r '.data.payNo' <<<"$BODY")"
pass "payNo=$PAY_NO payUrl=$PAY_URL"

step 10 "local channel callback completes the payment (signature verified)"
req GET "${PAY_URL#"$BASE"}"
require_code "mock payment callback"
pass "result=$(jq -r '.data' <<<"$BODY")"

step 11 "student owns the course"
# Ownership travels through the entitlement outbox, so allow a short propagation window.
OWNED=0
for _ in $(seq 1 20); do
  req GET "/api/v1/learning/my-courses?pageNum=1&pageSize=50" "$STUDENT_TOKEN"
  require_code "my courses"
  OWNED="$(jq -r --argjson cid "$COURSE_ID" '[.data.records[] | select(.courseId == $cid)] | length' <<<"$BODY")"
  [ "$OWNED" = "1" ] && break
  sleep 1
done
pass "ownedMatches=$OWNED"
[ "$OWNED" = "1" ] || die "course $COURSE_ID is missing from my-courses"

# The catalog only calls learn-service when a token is present, so anonymous browsing never
# touches the owned lookup that broke after the Boot 4 upgrade. Assert the signed-in list by
# business code and by the owned flag, not by HTTP status alone.
LIST_KEY="${COURSE_TITLE##* }"
req GET "/api/v1/courses?keyword=$LIST_KEY&page=1&size=20" "$STUDENT_TOKEN"
require_code "signed-in course list"
LIST_OWNED="$(jq -r --argjson cid "$COURSE_ID" \
  '[.data.items[] | select(.courseId == $cid and .owned == true)] | length' <<<"$BODY")"
[ "$LIST_OWNED" = "1" ] || die "course $COURSE_ID is not owned in the signed-in catalog"
pass "signedInListOwned=$LIST_OWNED"

step 12 "student resolves a signed playback URL"
req GET "/api/v1/courses/$COURSE_ID/chapters"
require_code "public chapters"
PLAY_LESSON="$(jq -r '.data.chapters[0].lessons[0].lessonId' <<<"$BODY")"
req GET "/api/v1/lessons/$PLAY_LESSON/play-token" "$STUDENT_TOKEN"
require_code "play token"
PLAY_URL="$(jq -r '.data.playUrl' <<<"$BODY")"
pass "lessonId=$PLAY_LESSON playUrl=${PLAY_URL%%\?*}"
[ -n "$PLAY_URL" ] && [ "$PLAY_URL" != "null" ] || die "play url is empty"

step 13 "student reports playback progress"
# The fixture is six seconds long, so the reported position must stay inside the video.
req POST /api/v1/learning/progress "$STUDENT_TOKEN" \
  "$(jq -nc --argjson lid "$PLAY_LESSON" '{lessonId:$lid,positionSeconds:5}')"
require_code "report progress"
pass "progress=$(jq -r '.data.positionSeconds // .data.position' <<<"$BODY")"

if [ "$SKIPPED_COUNT" -gt 0 ]; then
  printf '\nSMOKE FAIL: %s step(s) skipped\n' "$SKIPPED_COUNT"
  exit 1
fi
printf '\nSMOKE OK\n'
