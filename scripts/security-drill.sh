#!/usr/bin/env bash
# Runtime security drill for the L1-10 gate.
#
# Covers the assertions that can be judged against a local compose stack:
# forged identity headers, the four V7 admin:reconcile:* codes with real JWTs,
# cross-user (teacher and buyer) access, payment callback verification and
# replay, gateway rate limiting, the query injection surface and private
# object storage. Every assertion prints PASS/FAIL with its evidence; a single
# FAIL makes the script exit non-zero.
#
# Usage: bash scripts/security-drill.sh
# Inputs: LP_SEC_BASE_URL (default http://localhost:8080),
#         LP_SEC_MYSQL_CONTAINER (default lp-mysql).
# Side effects on the local stack: one reconcile row may be marked handled,
# one bill day close is attempted, and one order of demo_guest is paid so the
# callback replay can be checked. Nothing here is meant for staging or prod.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${LP_SEC_BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${LP_SEC_MYSQL_CONTAINER:-lp-mysql}"
TRADE_DIRECT="${LP_SEC_TRADE_DIRECT_URL:-http://127.0.0.1:8103}"
PASSWORD="${LP_SEC_PASSWORD:-Demo@123456}"

BODY_FILE="$(mktemp)"
RL_BODY="$(mktemp)"
trap 'rm -f "${BODY_FILE}" "${RL_BODY}"' EXIT

PASS=0
FAIL=0

ok() {
  PASS=$((PASS + 1))
  printf 'PASS  %s | %s\n' "$1" "$2"
}

bad() {
  FAIL=$((FAIL + 1))
  printf 'FAIL  %s | %s\n' "$1" "$2"
}

json_field() {
  python3 - "$BODY_FILE" "$@" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as handle:
        current = json.load(handle)
except Exception:
    print("")
    raise SystemExit(0)
for key in sys.argv[2:]:
    current = current.get(key) if isinstance(current, dict) else None
print("" if current is None else current)
PY
}

mysql_query() {
  local password
  password="$(docker inspect "${MYSQL_CONTAINER}" \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p')"
  if [ -z "${password}" ]; then
    printf 'ERROR 容器 %s 没有 MYSQL_ROOT_PASSWORD，无法解析越权用例所需的 id。\n' \
      "${MYSQL_CONTAINER}" >&2
    exit 1
  fi
  docker exec -e MYSQL_PWD="${password}" "${MYSQL_CONTAINER}" mysql -uroot -N -B -e "$1"
}

login() {
  curl -s -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"account\":\"$1\",\"password\":\"${PASSWORD}\"}" \
    | python3 -c 'import json,sys
d = json.load(sys.stdin)
data = d.get("data") or {}
print(data.get("accessToken", ""))'
}

# request METHOD PATH TOKEN BODY [extra curl args]: "-" means absent.
request() {
  local method=$1 path=$2 token=$3 body=${4:-}
  shift 4
  local args=(-s -o "${BODY_FILE}" -w '%{http_code}' -X "${method}" "${BASE}${path}")
  args+=(-H 'Content-Type: application/json')
  [ "${token}" != "-" ] && args+=(-H "Authorization: Bearer ${token}")
  [ "${body}" != "-" ] && args+=(-d "${body}")
  args+=("$@")
  HTTP="$(curl "${args[@]}")"
  CODE="$(json_field code)"
}

# raw_url URL [curl header args...]: used for direct service and MinIO calls.
raw_url() {
  local url=$1
  shift
  HTTP="$(curl -s -o "${BODY_FILE}" -w '%{http_code}' "$@" "${url}")"
  CODE="$(json_field code)"
}

expect_code() {
  local label=$1 expected=$2
  if [ "${CODE}" = "${expected}" ]; then
    ok "${label}" "http=${HTTP} code=${CODE}"
  else
    bad "${label}" "expected code=${expected}, got http=${HTTP} code=${CODE}"
  fi
}

expect_not_code() {
  local label=$1 forbidden=$2
  if [ "${CODE}" != "${forbidden}" ] && [ "${CODE}" != "none" ]; then
    ok "${label}" "http=${HTTP} code=${CODE}"
  else
    bad "${label}" "expected a business rejection other than ${forbidden}, got http=${HTTP} code=${CODE}"
  fi
}

expect_http() {
  local label=$1 expected=$2
  if [ "${HTTP}" = "${expected}" ]; then
    ok "${label}" "http=${HTTP} code=${CODE}"
  else
    bad "${label}" "expected http=${expected}, got http=${HTTP} code=${CODE}"
  fi
}

if ! curl -fsS -o /dev/null "${BASE}/actuator/health"; then
  printf 'ERROR 网关不可用：%s/actuator/health。先执行 bash scripts/dev-up.sh。\n' "${BASE}" >&2
  exit 1
fi

ADMIN="$(login demo_admin)"
OPS="$(login demo_ops)"
BUYER="$(login demo_buyer)"
GUEST="$(login demo_guest)"
TEACHER_LIN="$(login demo_teacher_lin)"
TEACHER_SU="$(login demo_teacher_su)"
for pair in "demo_admin:${ADMIN}" "demo_ops:${OPS}" "demo_buyer:${BUYER}" \
  "demo_guest:${GUEST}" "demo_teacher_lin:${TEACHER_LIN}" "demo_teacher_su:${TEACHER_SU}"; do
  if [ -z "${pair#*:}" ]; then
    printf 'ERROR 账号 %s 登录失败，安全用例无法判定，不做静默跳过。\n' "${pair%%:*}" >&2
    exit 1
  fi
done

RECHECK_ID="$(mysql_query "SELECT id FROM lp_oms.payment_reconcile ORDER BY id LIMIT 1")"
PENDING_ID="$(mysql_query \
  "SELECT id FROM lp_oms.payment_reconcile WHERE status = 2 ORDER BY id LIMIT 1")"
BILL_DAY="$(mysql_query "SELECT bill_date FROM lp_oms.payment_reconcile WHERE id = ${RECHECK_ID}")"
LIN="$(mysql_query "
  SELECT c.id, l.id FROM lp_cms.course c
    JOIN lp_cms.teacher_profile t ON t.id = c.owner_teacher_id
    JOIN lp_cms.course_lesson l ON l.course_id = c.id
  WHERE t.user_id = 21 AND l.video_url <> ''
  ORDER BY l.id LIMIT 1")"
SU="$(mysql_query "
  SELECT c.id, l.id FROM lp_cms.course c
    JOIN lp_cms.teacher_profile t ON t.id = c.owner_teacher_id
    JOIN lp_cms.course_lesson l ON l.course_id = c.id
  WHERE t.user_id = 22 AND l.video_url <> ''
  ORDER BY l.id LIMIT 1")"
BUYER_ORDER="$(mysql_query "
  SELECT o.id FROM lp_oms.trade_order o
    JOIN lp_ums.sys_user u ON u.id = o.user_id
  WHERE u.username = 'demo_buyer' ORDER BY o.id DESC LIMIT 1")"
OBJECT_KEY="$(mysql_query "
  SELECT l.video_url FROM lp_cms.course_lesson l WHERE l.id = ${LIN#*	}")"
GUEST_COURSE="$(mysql_query "
  SELECT c.id FROM lp_cms.course c
  WHERE c.status = 2 AND c.price > 0
    AND NOT EXISTS (SELECT 1 FROM lp_lms.user_entitlement e
                      JOIN lp_ums.sys_user u ON u.id = e.user_id
                    WHERE e.course_id = c.id AND u.username = 'demo_guest')
  ORDER BY c.id LIMIT 1")"

for pair in "reconcile:${RECHECK_ID}" "bill-day:${BILL_DAY}" "lin-course-lesson:${LIN}" \
  "su-course-lesson:${SU}" "buyer-order:${BUYER_ORDER}" "object-key:${OBJECT_KEY}"; do
  if [ -z "${pair#*:}" ]; then
    printf 'ERROR 缺少用例数据 %s，无法判定；先执行 bash scripts/seed/seed-demo.sh。\n' \
      "${pair%%:*}" >&2
    exit 1
  fi
done

read -r LIN_COURSE LIN_LESSON <<< "${LIN}"
read -r SU_COURSE SU_LESSON <<< "${SU}"
SU_CHAPTER="$(mysql_query "SELECT chapter_id FROM lp_cms.course_lesson WHERE id = ${SU_LESSON}")"
LIN_CHAPTER="$(mysql_query "SELECT chapter_id FROM lp_cms.course_lesson WHERE id = ${LIN_LESSON}")"

printf '环境：%s，对账行=%s，账单日=%s\n' "${BASE}" "${RECHECK_ID}" "${BILL_DAY}"

echo '== 1. 伪造网关注入身份头'
raw_url "${BASE}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view'
expect_code 'gateway anonymous with forged identity headers' 40101

raw_url "${BASE}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H "Authorization: Bearer ${BUYER}" -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view'
expect_code 'gateway student token with forged admin headers' 40301

raw_url "${TRADE_DIRECT}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view,admin:reconcile:recheck,admin:reconcile:handle,admin:reconcile:close'
expect_not_code 'direct service call with forged identity headers' 0

raw_url "${TRADE_DIRECT}/internal/v1/orders/stats" -H 'X-Internal-Token: wrong'
expect_code 'internal endpoint without the shared token' 40101

echo '== 2. V7 admin:reconcile:* 权限码（真实 JWT 走 HTTP）'
request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${ADMIN}" -
expect_code 'admin:reconcile:view via admin JWT' 0

request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${OPS}" -
expect_code 'admin:reconcile:view via ops JWT (V7 grants OPS)' 0

request POST "/api/v1/reconcile/${RECHECK_ID}/recheck" "${ADMIN}" -
expect_not_code 'admin:reconcile:recheck via admin JWT' 40301

if [ -n "${PENDING_ID}" ]; then
  request POST "/api/v1/reconcile/${PENDING_ID}/handle" "${ADMIN}" -
else
  request POST "/api/v1/reconcile/${RECHECK_ID}/handle" "${ADMIN}" -
fi
expect_not_code 'admin:reconcile:handle via admin JWT' 40301

request POST '/api/v1/reconcile/settlements' "${ADMIN}" \
  "{\"channel\":1,\"billDate\":\"${BILL_DAY}\"}"
expect_not_code 'admin:reconcile:close via admin JWT' 40301

request POST '/api/v1/reconcile/settlements/999999/void' "${ADMIN}" \
  '{"reason":"security drill"}'
expect_code 'void gate reached with admin role and close code' 40401

request POST '/api/v1/reconcile/settlements' "${OPS}" \
  "{\"channel\":1,\"billDate\":\"${BILL_DAY}\"}"
expect_code 'close rejected for ops JWT (V7 grants close to ADMIN only)' 40301

request POST '/api/v1/reconcile/settlements/999999/void' "${OPS}" \
  '{"reason":"security drill"}'
expect_code 'void rejected for ops JWT' 40301

request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${BUYER}" -
expect_code 'reconcile rejected for student JWT' 40301

echo '== 3. 跨用户越权'
request GET "/api/v1/teacher/courses/${LIN_COURSE}" "${TEACHER_LIN}" -
expect_code 'teacher reads own course' 0

request GET "/api/v1/teacher/courses/${SU_COURSE}" "${TEACHER_LIN}" -
expect_code 'teacher reads another teacher course' 40301

request GET \
  "/api/v1/teacher/courses/${LIN_COURSE}/chapters/${LIN_CHAPTER}/lessons/${LIN_LESSON}/play-url" \
  "${TEACHER_LIN}" -
expect_code 'teacher signs a play url for an owned lesson' 0

request GET \
  "/api/v1/teacher/courses/${SU_COURSE}/chapters/${SU_CHAPTER}/lessons/${SU_LESSON}/play-url" \
  "${TEACHER_LIN}" -
expect_code 'teacher signs a play url for another teacher lesson' 40301

request GET "/api/v1/teacher/courses" "${BUYER}" -
expect_code 'student lists teacher courses' 40301

request GET "/api/v1/orders/${BUYER_ORDER}" "${GUEST}" -
expect_code 'student reads another student order' 40301

request GET "/api/v1/orders/${BUYER_ORDER}" "${BUYER}" -
expect_code 'student reads own order' 0

echo '== 4. 上传会话与对象存储越权'
request POST \
  "/api/v1/teacher/courses/${SU_COURSE}/chapters/${SU_CHAPTER}/lessons/${SU_LESSON}/upload-sessions" \
  "${TEACHER_LIN}" '{"expectedSizeBytes":1048576}'
expect_code 'teacher opens an upload session on another teacher lesson' 40301

request POST \
  "/api/v1/teacher/courses/${LIN_COURSE}/chapters/${LIN_CHAPTER}/lessons/${LIN_LESSON}/upload-sessions" \
  "${TEACHER_LIN}" '{"expectedSizeBytes":1048576}'
expect_not_code 'teacher opens an upload session on an owned lesson' 40301

raw_url "http://127.0.0.1:9000/lp-media/${OBJECT_KEY}"
expect_http 'private bucket object without a signature' 403

echo '== 5. 支付回调验签与重放'
PAY_SECRET="$(docker inspect lp-trade-service \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^LP_PAY_MOCK_SECRET=//p')"
if [ -z "${PAY_SECRET}" ]; then
  printf 'ERROR 读不到 lp-trade-service 的 LP_PAY_MOCK_SECRET，回调验签无法判定。\n' >&2
  exit 1
fi
sign() {
  printf '%s' "payNo=$1&orderNo=$2&amount=$3" \
    | openssl dgst -sha256 -hmac "${PAY_SECRET}" -r | cut -d' ' -f1
}
raw_url "${BASE}/api/v1/payments/mock/callback?payNo=P-DRILL&orderNo=LP-DRILL&amount=1.00&signature=deadbeef"
expect_code 'mock callback with a forged signature' 62016

raw_url "${BASE}/api/v1/payments/mock/callback?payNo=P-DRILL&orderNo=LP-DRILL&amount=1.00&signature=$(sign P-DRILL LP-DRILL 0.01)"
expect_not_code 'mock callback with a tampered amount' 0

if [ -n "${GUEST_COURSE}" ]; then
  request POST '/api/v1/orders' "${GUEST}" \
    "{\"courseId\":${GUEST_COURSE},\"idempotencyKey\":\"sec-$(date +%s)-${RANDOM}\"}"
  ORDER_NO="$(json_field data orderNo)"
  if [ -z "${ORDER_NO}" ]; then
    bad 'guest creates an order for the callback replay' "http=${HTTP} code=${CODE}"
  else
    ok 'guest creates an order for the callback replay' "orderNo=${ORDER_NO}"
    PAY_FEN="$(json_field data payAmountFen)"
    AMOUNT="$(python3 -c 'import sys;print(f"{int(sys.argv[1]) / 100:.2f}")' "${PAY_FEN:-0}")"
    request POST "/api/v1/payments/${ORDER_NO}/pay" "${GUEST}" - \
      -H "Idempotency-Key: sec-pay-$(date +%s)-${RANDOM}"
    PAY_NO="$(json_field data payNo)"
    if [ -z "${PAY_NO}" ] || [ -z "${AMOUNT}" ]; then
      bad 'payment initiation returns a signable payload' "http=${HTTP} code=${CODE}"
    else
      SIGNATURE="$(sign "${PAY_NO}" "${ORDER_NO}" "${AMOUNT}")"
      CALLBACK="payNo=${PAY_NO}&orderNo=${ORDER_NO}&amount=${AMOUNT}&signature=${SIGNATURE}"
      raw_url "${BASE}/api/v1/payments/mock/callback?${CALLBACK}"
      expect_code 'signed mock callback applies the payment' 0
      # The entitlement is written by the payment outbox relay, so wait for it
      # before replaying instead of reporting a false negative.
      GRANTS=0
      for _ in $(seq 1 15); do
        GRANTS="$(mysql_query "
          SELECT COUNT(*) FROM lp_lms.user_entitlement e
            JOIN lp_ums.sys_user u ON u.id = e.user_id
          WHERE u.username = 'demo_guest' AND e.course_id = ${GUEST_COURSE}")"
        [ "${GRANTS}" = "1" ] && break
        sleep 1
      done
      raw_url "${BASE}/api/v1/payments/mock/callback?${CALLBACK}"
      expect_code 'replayed mock callback stays idempotent' 0
      sleep 2
      GRANTS_AFTER="$(mysql_query "
        SELECT COUNT(*) FROM lp_lms.user_entitlement e
          JOIN lp_ums.sys_user u ON u.id = e.user_id
        WHERE u.username = 'demo_guest' AND e.course_id = ${GUEST_COURSE}")"
      if [ "${GRANTS}" = "1" ] && [ "${GRANTS_AFTER}" = "1" ]; then
        ok 'replay grants exactly one entitlement' \
          "before=${GRANTS} after=${GRANTS_AFTER}"
      else
        bad 'replay grants exactly one entitlement' \
          "expected 1 before and after, got before=${GRANTS} after=${GRANTS_AFTER}"
      fi
    fi
  fi
else
  bad 'guest creates an order for the callback replay' \
    'demo_guest already owns every paid course; reseed before rerunning'
fi

echo '== 6. 查询注入面与分页上限'
INJECT="$(python3 -c 'import urllib.parse;print(urllib.parse.quote("\x27 OR 1=1--"))')"
request GET "/api/v1/courses?keyword=${INJECT}&size=1000000" "${BUYER}" -
if [ "${HTTP}" -lt 500 ] && ! grep -qE 'SQLSyntaxError|BadSqlGrammar|java\.sql' "${BODY_FILE}"; then
  ok 'course keyword injection does not reach SQL errors' \
    "http=${HTTP} code=${CODE} records=$(json_field data total)"
else
  bad 'course keyword injection does not reach SQL errors' "http=${HTTP} code=${CODE}"
fi

request GET '/api/v1/courses?keyword=zzzz&page=1&size=1000000' "${BUYER}" -
PAGE_SIZE="$(json_field data pageSize)"
if [ "${HTTP}" -lt 500 ] && { [ -z "${PAGE_SIZE}" ] || [ "${PAGE_SIZE}" -le 100 ]; }; then
  ok 'oversized page size is capped, not executed' "http=${HTTP} pageSize=${PAGE_SIZE:-n/a}"
else
  bad 'oversized page size is capped, not executed' "http=${HTTP} pageSize=${PAGE_SIZE}"
fi

request GET '/api/v1/orders?pageNum=1&pageSize=1000000' "${BUYER}" -
PAGE_SIZE="$(json_field data pageSize)"
if [ "${HTTP}" -lt 500 ] && { [ -z "${PAGE_SIZE}" ] || [ "${PAGE_SIZE}" -le 100 ]; }; then
  ok 'order list rejects oversized page size' "http=${HTTP} pageSize=${PAGE_SIZE:-n/a}"
else
  bad 'order list rejects oversized page size' "http=${HTTP} pageSize=${PAGE_SIZE}"
fi

echo '== 7. 网关限流'
LIMIT="$(docker inspect lp-gateway \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^LP_GATEWAY_RATE_LIMIT_PER_MINUTE=//p')"
LIMIT="${LIMIT:-300}"
GOT_429=0
# The bucket refills continuously, so a sequential loop never drains it.
# Fire the burst in parallel, which is also how a real abuser would.
seq 1 $((LIMIT * 2)) | xargs -P 20 -I{} curl -s -o "${BODY_FILE}.{}" -w '%{http_code}\n' \
  -H "Authorization: Bearer ${GUEST}" "${BASE}/api/v1/courses?size=1" \
  | sort | uniq -c > "${RL_BODY}"
GOT_429="$(awk '$2 == 429 {print $1}' "${RL_BODY}")"
GOT_429="${GOT_429:-0}"
rm -f "${BODY_FILE}".*
if [ "${GOT_429}" -gt 0 ]; then
  STATUS="$(curl -s -o "${BODY_FILE}" -w '%{http_code}' \
    -H "Authorization: Bearer ${GUEST}" "${BASE}/api/v1/courses?size=1")"
  RL_CODE="$(json_field code)"
  if [ "${RL_CODE}" = "42901" ]; then
    ok 'gateway rate limit returns 42901' "429 responses=${GOT_429} of $((LIMIT * 2))"
  else
    bad 'gateway rate limit returns 42901' "429 responses=${GOT_429}, body code=${RL_CODE}"
  fi
else
  bad 'gateway rate limit returns 42901' \
    "no 429 in $((LIMIT * 2)) requests at limit-per-minute=${LIMIT}"
fi

printf '\nSUMMARY pass=%s fail=%s\n' "${PASS}" "${FAIL}"
[ "${FAIL}" -eq 0 ]
