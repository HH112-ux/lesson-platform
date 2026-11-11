#!/usr/bin/env bash
# Payment and traffic drill for the L1-10 gate.
#
# Threat surface: payment callback signature forgery, amount tampering and
# replay, the query injection surface, pagination caps and the gateway rate
# limit. Every assertion prints PASS/FAIL with its evidence; one FAIL exits
# non-zero. Real Alipay verification needs merchant credentials and is not
# covered here, only the mock channel path.
#
# Usage: bash scripts/security/payment-drill.sh
# Side effects: one order of demo_guest is paid to check callback replay.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=lib.sh
. "${ROOT}/scripts/security/lib.sh"

if ! curl -fsS -o /dev/null "${SEC_BASE}/actuator/health"; then
  printf 'ERROR 网关不可用：%s/actuator/health，先执行 bash scripts/dev-up.sh。\n' \
    "${SEC_BASE}" >&2
  exit 1
fi

BUYER="$(login demo_buyer)"
GUEST="$(login demo_guest)"
require_login demo_buyer "${BUYER}"
require_login demo_guest "${GUEST}"

GUEST_COURSE="$(mysql_query "
  SELECT c.id FROM lp_cms.course c
  WHERE c.status = 2 AND c.price > 0
    AND NOT EXISTS (SELECT 1 FROM lp_lms.user_entitlement e
                      JOIN lp_ums.sys_user u ON u.id = e.user_id
                    WHERE e.course_id = c.id AND u.username = 'demo_guest')
  ORDER BY c.id LIMIT 1")"

echo '== 1. 支付回调验签、金额篡改与重放'
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

raw_url "${SEC_BASE}/api/v1/payments/mock/callback?payNo=P-DRILL&orderNo=LP-DRILL&amount=1.00&signature=deadbeef"
expect_code 'mock callback with a forged signature' 62016

raw_url "${SEC_BASE}/api/v1/payments/mock/callback?payNo=P-DRILL&orderNo=LP-DRILL&amount=1.00&signature=$(sign P-DRILL LP-DRILL 0.01)"
expect_not_code 'mock callback with a tampered amount' 0

if [ -z "${GUEST_COURSE}" ]; then
  bad 'guest creates an order for the callback replay' \
    'demo_guest already owns every paid course; reseed before rerunning'
else
  request POST '/api/v1/orders' "${GUEST}" \
    "{\"courseId\":${GUEST_COURSE},\"idempotencyKey\":\"sec-$(date +%s)-${RANDOM}\"}"
  ORDER_NO="$(json_field data orderNo)"
  PAY_FEN="$(json_field data payAmountFen)"
  AMOUNT="$(python3 -c 'import sys;print(f"{int(sys.argv[1]) / 100:.2f}")' "${PAY_FEN:-0}")"
  if [ -z "${ORDER_NO}" ]; then
    bad 'guest creates an order for the callback replay' "http=${HTTP} code=${CODE}"
  else
    ok 'guest creates an order for the callback replay' "orderNo=${ORDER_NO}"
    request POST "/api/v1/payments/${ORDER_NO}/pay" "${GUEST}" - \
      -H "Idempotency-Key: sec-pay-$(date +%s)-${RANDOM}"
    PAY_NO="$(json_field data payNo)"
    if [ -z "${PAY_NO}" ]; then
      bad 'payment initiation returns a signable payload' "http=${HTTP} code=${CODE}"
    else
      SIGNATURE="$(sign "${PAY_NO}" "${ORDER_NO}" "${AMOUNT}")"
      CALLBACK="payNo=${PAY_NO}&orderNo=${ORDER_NO}&amount=${AMOUNT}&signature=${SIGNATURE}"
      raw_url "${SEC_BASE}/api/v1/payments/mock/callback?${CALLBACK}"
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
      raw_url "${SEC_BASE}/api/v1/payments/mock/callback?${CALLBACK}"
      expect_code 'replayed mock callback stays idempotent' 0
      sleep 2
      GRANTS_AFTER="$(mysql_query "
        SELECT COUNT(*) FROM lp_lms.user_entitlement e
          JOIN lp_ums.sys_user u ON u.id = e.user_id
        WHERE u.username = 'demo_guest' AND e.course_id = ${GUEST_COURSE}")"
      if [ "${GRANTS}" = "1" ] && [ "${GRANTS_AFTER}" = "1" ]; then
        ok 'replay grants exactly one entitlement' "before=${GRANTS} after=${GRANTS_AFTER}"
      else
        bad 'replay grants exactly one entitlement' \
          "expected 1 before and after, got before=${GRANTS} after=${GRANTS_AFTER}"
      fi
    fi
  fi
fi

echo '== 2. 查询注入面与分页上限'
INJECT="$(python3 -c 'import urllib.parse;print(urllib.parse.quote("\x27 OR 1=1--"))')"
request GET "/api/v1/courses?keyword=${INJECT}&size=1000000" "${BUYER}" -
if [ "${HTTP}" -lt 500 ] && ! grep -qE 'SQLSyntaxError|BadSqlGrammar|java\.sql' "${SEC_BODY}"; then
  ok 'course keyword injection does not reach SQL errors' "http=${HTTP} code=${CODE}"
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

echo '== 3. 网关限流'
LIMIT="$(docker inspect lp-gateway \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^LP_GATEWAY_RATE_LIMIT_PER_MINUTE=//p')"
LIMIT="${LIMIT:-300}"
RATE_DIR="$(mktemp -d)"
trap 'rm -rf "${SEC_BODY}" "${RATE_DIR}"' EXIT
# The bucket refills continuously, so a sequential loop never drains it.
# Fire the burst in parallel, which is also how a real abuser would, and keep
# each body so the 429 payload is read from the response that actually failed.
seq 1 $((LIMIT * 2)) | xargs -P 20 -n 1 bash -c '
  status=$(curl -s -o "'"${RATE_DIR}"'/$0" -w "%{http_code}" \
    -H "Authorization: Bearer '"${GUEST}"'" "'"${SEC_BASE}"'/api/v1/courses?size=1")
  echo "${status} $0"
' _ > "${RATE_DIR}/status.txt"
GOT_429="$(awk '$1 == 429' "${RATE_DIR}/status.txt" | wc -l | tr -d ' ')"
IDX="$(awk '$1 == 429 {print $2; exit}' "${RATE_DIR}/status.txt")"
if [ "${GOT_429}" -gt 0 ]; then
  RL_CODE="$(python3 - "${RATE_DIR}/${IDX}" <<'PY'
import json
import sys
try:
    with open(sys.argv[1], encoding="utf-8") as handle:
        print(json.load(handle).get("code"))
except Exception:
    print("none")
PY
)"
  if [ "${RL_CODE}" = "42901" ]; then
    ok 'gateway rate limit returns 42901' "429 responses=${GOT_429} of $((LIMIT * 2))"
  else
    bad 'gateway rate limit returns 42901' "429 responses=${GOT_429}, body code=${RL_CODE}"
  fi
else
  bad 'gateway rate limit returns 42901' \
    "no 429 in $((LIMIT * 2)) requests at limit-per-minute=${LIMIT}"
fi

summary
