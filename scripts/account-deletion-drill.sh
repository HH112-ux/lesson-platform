#!/usr/bin/env bash
#
# Account deletion drill for SPEC-PLAT-004.
#
# Proves the cooling-off flow end to end against the local stack:
#   1. a password account and a miniapp account apply for cancellation;
#   2. the request row keeps a 7 day deadline while the account leaves ACTIVE;
#   3. moving effective_at into the past lets the scheduled job execute each request once;
#   4. the account is anonymised in place, the WeChat binding and the payer openid are rewritten,
#      and no business row is deleted;
#   5. sessions of the deleted account are refused with 67001 (/me and refresh);
#   6. the original username, email, phone and WeChat openid can be registered again;
#   7. a repeated anonymisation rewrites nothing.
#
# Usage:
#   bash scripts/account-deletion-drill.sh
#
# Prerequisites:
#   bash scripts/dev-up.sh          # mysql, redis, nacos, gateway, auth, trade, learn
#   bash scripts/seed/seed-demo.sh  # at least one published course with price > 0
#
# Environment variables. 变量名均按 application.yml 属性名松弛绑定核对，逐个对照如下：
#   property lp.auth.account-deletion.execution-enabled
#     yml 78  execution-enabled: ${LP_AUTH_CANCELLATION_EXECUTION_ENABLED:false}
#     alias also accepted by relaxed binding: LP_AUTH_ACCOUNT_DELETION_EXECUTION_ENABLED
#   property lp.auth.account-deletion.anonymize-salt
#     yml 82  anonymize-salt: ${LP_ANONYMIZE_SALT:}      (blank salt aborts startup on purpose)
#   property lp.auth.miniapp.enabled
#     yml 55  enabled: ${LP_MINIAPP_LOGIN_ENABLED:false}
#   property lp.auth.miniapp.mock-enabled
#     yml 56  mock-enabled: ${LP_MINIAPP_LOGIN_MOCK_ENABLED:false}
#   property lp.auth.account-deletion.scan-delay-ms has no yml placeholder; the drill keeps the
#   default 60 s delay and 30 s initial delay and therefore waits up to 150 s in step 5.
#
# infra/docker-compose.yml must forward those four switches to the auth-service container,
# exactly like trade-service forwards LP_PAY_MOCK_ENABLED.  Without the passthrough the container
# keeps its defaults and the drill would silently pass against a job that never ran, so step 1
# reads them back with printenv and aborts.  Required block under auth-service > environment:
#   LP_AUTH_CANCELLATION_EXECUTION_ENABLED: ${LP_AUTH_CANCELLATION_EXECUTION_ENABLED:-false}
#   LP_ANONYMIZE_SALT: ${LP_ANONYMIZE_SALT:-}
#   LP_MINIAPP_LOGIN_ENABLED: ${LP_MINIAPP_LOGIN_ENABLED:-false}
#   LP_MINIAPP_LOGIN_MOCK_ENABLED: ${LP_MINIAPP_LOGIN_MOCK_ENABLED:-false}
#
# Two assertions need a fixture because the production path cannot reach them any more:
#   * payment_record.payer_openid has no writer in trade, so step 3 seeds it with SQL.  The
#     anonymised value still comes from the real job; the seed only stands in for the missing
#     miniapp payment path.
#   * login() checks the password before the status gate and anonymisation randomises that
#     password, so a deleted account can never be reached through /auth/login again.  Step 7
#     therefore proves 67001 on /me and refresh with the real account, and proves the login-path
#     gate with a status-only fixture account (step 7c).
#
# Deferred to L1-10: two-replica concurrency (claim executed exactly once across processes).
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${LP_GATEWAY_URL:-http://localhost:8080}"
AUTH_URL="${LP_AUTH_URL:-http://localhost:8101}"
TRADE_INTERNAL="${LP_TRADE_URL:-http://localhost:8103}"
MYSQL_PASSWORD="${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}"
INTERNAL_TOKEN="${LP_INTERNAL_TOKEN:-lp_internal_dev_token}"
DEMO_PASSWORD="${LP_DEMO_PASSWORD:-Demo@123456}"
RUN_ID="$(date +%Y%m%d%H%M%S)"
SALT="${LP_DRILL_SALT:-lp_drill_salt_${RUN_ID}}"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/infra/.env")
AUTH_TOGGLED=0

step() { printf '\n\033[1;34m== %s\033[0m\n' "$1"; }
pass() { printf '\033[0;32m  PASS\033[0m %s\n' "$1"; }
fail() { printf '\033[0;31m  FAIL\033[0m %s\n' "$1"; }
info() { printf '  info %s\n' "$1"; }
die() { printf '\033[0;31mABORT\033[0m %s\n' "$1" >&2; exit 1; }

expect_eq() { # label expected actual
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1: expected [$2], got [$3]"; exit 1; fi
}

sql() {
  "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"$MYSQL_PASSWORD" -N -B -e "$1" 2>/dev/null
}

json_get() { # dotted path; reads stdin
  python3 -c 'import json,sys
d=json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d=d[k]
print(d)' "$1"
}

api() { # method path token body
  local args=(-s -X "$1" "$BASE$2" -H 'Content-Type: application/json')
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-d "$4")
  curl "${args[@]}"
}

wait_sql() { # sql expected attempts
  local i
  for i in $(seq 1 "$3"); do
    [ "$(sql "$1")" = "$2" ] && return 0
    sleep 2
  done
  return 1
}

wait_http() { # url attempts
  local i
  for i in $(seq 1 "$2"); do
    curl -sf "$1" >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}

counts() {
  sql "SELECT CONCAT_WS('|', (SELECT COUNT(*) FROM lp_ums.sys_user),
        (SELECT COUNT(*) FROM lp_ums.sys_user_wechat),
        (SELECT COUNT(*) FROM lp_oms.payment_record),
        (SELECT COUNT(*) FROM lp_oms.trade_order),
        (SELECT COUNT(*) FROM lp_ums.user_account_cancellation))"
}

restore_auth() {
  if [ "$AUTH_TOGGLED" = 1 ]; then
    printf '\n\033[1;34m== restore auth-service\033[0m\n'
    "${COMPOSE[@]}" up -d --no-deps --force-recreate auth-service >/dev/null 2>&1
    wait_http "$AUTH_URL/actuator/health" 40 || printf 'WARN auth-service not healthy after restore\n'
  fi
}
trap restore_auth EXIT

step "0. stack and fixture checks"
wait_http "$BASE/api/v1/courses" 20 || die "gateway is not serving $BASE"
COURSE_ID="$(sql 'SELECT id FROM lp_cms.course WHERE status = 2 AND price > 0 ORDER BY id LIMIT 1')"
[ -n "$COURSE_ID" ] || die "no published paid course; run scripts/seed/seed-demo.sh first"
pass "gateway reachable, paid course id=$COURSE_ID"
info "baseline rows $(counts)"

step "1. enable the execution job and verify the switch reached the process"
LP_AUTH_CANCELLATION_EXECUTION_ENABLED=true \
LP_ANONYMIZE_SALT="$SALT" \
LP_MINIAPP_LOGIN_ENABLED=true \
LP_MINIAPP_LOGIN_MOCK_ENABLED=true \
  "${COMPOSE[@]}" up -d --no-deps --force-recreate auth-service >/dev/null 2>&1
wait_http "$AUTH_URL/actuator/health" 40 || die "auth-service did not start; a blank salt aborts the execution job on purpose"
for pair in "LP_AUTH_CANCELLATION_EXECUTION_ENABLED=true" "LP_ANONYMIZE_SALT=$SALT" \
            "LP_MINIAPP_LOGIN_ENABLED=true" "LP_MINIAPP_LOGIN_MOCK_ENABLED=true"; do
  name="${pair%%=*}"
  want="${pair#*=}"
  got="$(docker exec lp-auth-service printenv "$name" 2>/dev/null)"
  [ "$got" = "$want" ] || die "$name not forwarded to auth-service (got '[$got]'); add the four passthrough lines listed in the header"
done
AUTH_TOGGLED=1
pass "execution switch, salt and miniapp mock are present inside the container"

step "2. create a password account (A) and a miniapp account (B)"
USER_A="cd${RUN_ID}"                       # 16 chars, matches ^[A-Za-z0-9]{4,20}$
MAIL_A="cd${RUN_ID}@example.com"
PHONE_A="139${RUN_ID: -8}"
CODE_B="cdrill-code-${RUN_ID}"
REG_A="$(api POST /api/v1/auth/register '' "{\"username\":\"$USER_A\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"drill a\",\"email\":\"$MAIL_A\",\"phone\":\"$PHONE_A\"}")"
expect_eq "register A" 0 "$(printf '%s' "$REG_A" | json_get code)"
ID_A="$(sql "SELECT id FROM lp_ums.sys_user WHERE username='$USER_A'")"
[ -n "$ID_A" ] || die "account A not found in lp_ums.sys_user"
LOGIN_A="$(api POST /api/v1/auth/login '' "{\"account\":\"$USER_A\",\"password\":\"$DEMO_PASSWORD\"}")"
TOKEN_A="$(printf '%s' "$LOGIN_A" | json_get data.accessToken)"
REFRESH_A="$(printf '%s' "$LOGIN_A" | json_get data.refreshToken)"
LOGIN_B="$(api POST /api/v1/auth/miniapp-login '' "{\"code\":\"$CODE_B\"}")"
expect_eq "miniapp login B" 0 "$(printf '%s' "$LOGIN_B" | json_get code)"
TOKEN_B="$(printf '%s' "$LOGIN_B" | json_get data.accessToken)"
ID_B="$(sql "SELECT id FROM lp_ums.sys_user_wechat WHERE openid='mock-openid-$CODE_B'")"
[ -n "$ID_B" ] || die "miniapp binding for B not found"
pass "A id=$ID_A, B id=$ID_B, wechat openid=mock-openid-$CODE_B"

step "3. A buys and pays one course, then the payer openid fixture is seeded"
ORDER="$(api POST /api/v1/orders "$TOKEN_A" "{\"courseId\":$COURSE_ID,\"idempotencyKey\":\"drill-order-$RUN_ID\"}")"
expect_eq "create order" 0 "$(printf '%s' "$ORDER" | json_get code)"
ORDER_NO="$(printf '%s' "$ORDER" | json_get data.orderNo)"
PAY="$(curl -s -X POST "$BASE/api/v1/payments/$ORDER_NO/pay" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_A" -H "Idempotency-Key: drill-pay-$RUN_ID")"
PAY_URL="$(printf '%s' "$PAY" | json_get data.payParams.payUrl)"
case "$PAY_URL" in
  http*) CALLBACK_URL="$PAY_URL" ;;
  *) CALLBACK_URL="$BASE$PAY_URL" ;;
esac
curl -s "$CALLBACK_URL" >/dev/null || die "mock payment callback failed: $CALLBACK_URL"
expect_eq "order paid" 1 "$(sql "SELECT status FROM lp_oms.trade_order WHERE order_no='$ORDER_NO'")"
PAY_ID="$(sql "SELECT id FROM lp_oms.payment_record WHERE order_id=(SELECT id FROM lp_oms.trade_order WHERE order_no='$ORDER_NO') LIMIT 1")"
[ -n "$PAY_ID" ] || die "payment row for $ORDER_NO not found"
FIXTURE_OPENID="mock-openid-${RUN_ID}"
sql "UPDATE lp_oms.payment_record SET payer_openid='$FIXTURE_OPENID' WHERE id=$PAY_ID" >/dev/null
expect_eq "payer_openid fixture written (not an interface write)" "$FIXTURE_OPENID" \
  "$(sql "SELECT payer_openid FROM lp_oms.payment_record WHERE id=$PAY_ID")"
pass "order=$ORDER_NO payment=$PAY_ID"

step "4. both accounts apply for cancellation"
APPLY_A="$(api POST /api/v1/auth/account-cancellation "$TOKEN_A" '{}')"
expect_eq "apply A" 0 "$(printf '%s' "$APPLY_A" | json_get code)"
expect_eq "apply A status" 0 "$(printf '%s' "$APPLY_A" | json_get data.status)"
REMAIN_A="$(printf '%s' "$APPLY_A" | json_get data.remainingSeconds)"
if [ "$REMAIN_A" -gt 604000 ] && [ "$REMAIN_A" -le 604800 ]; then
  pass "apply A remainingSeconds=$REMAIN_A (7 day cool-off)"
else
  fail "apply A remainingSeconds=$REMAIN_A outside the expected 7 day window"
  exit 1
fi
APPLY_B="$(api POST /api/v1/auth/account-cancellation "$TOKEN_B" '{}')"
expect_eq "apply B" 0 "$(printf '%s' "$APPLY_B" | json_get code)"
expect_eq "A status cooling off" 2 "$(sql "SELECT status FROM lp_ums.sys_user WHERE id=$ID_A")"
expect_eq "B status cooling off" 2 "$(sql "SELECT status FROM lp_ums.sys_user WHERE id=$ID_B")"
expect_eq "A request pending" 0 "$(sql "SELECT status FROM lp_ums.user_account_cancellation WHERE user_id=$ID_A")"
expect_eq "A executed_at empty" "" "$(sql "SELECT IFNULL(executed_at,'') FROM lp_ums.user_account_cancellation WHERE user_id=$ID_A")"
COUNT_BEFORE_EXECUTION="$(counts)"
info "rows before execution $COUNT_BEFORE_EXECUTION"

step "5. move effective_at into the past and wait for the scheduled job"
sql "UPDATE lp_ums.user_account_cancellation SET effective_at = DATE_SUB(NOW(3), INTERVAL 5 MINUTE) WHERE user_id IN ($ID_A,$ID_B)" >/dev/null
STARTED_AT="$(date +%s)"
wait_sql "SELECT status FROM lp_ums.user_account_cancellation WHERE user_id=$ID_A" 2 75 \
  || die "request A was not executed within 150 s (check auth-service logs and the salt)"
wait_sql "SELECT status FROM lp_ums.user_account_cancellation WHERE user_id=$ID_B" 2 30 \
  || die "request B was not executed within 60 s"
ELAPSED=$(( $(date +%s) - STARTED_AT ))
pass "job executed both requests in ${ELAPSED}s"
expect_eq "A executed_at recorded" 1 \
  "$(sql "SELECT IFNULL(executed_at,'') <> '' FROM lp_ums.user_account_cancellation WHERE user_id=$ID_A")"
METRIC="$(curl -s "$AUTH_URL/actuator/prometheus" | grep '^lp_auth_account_cancellation_executed_total' | head -1)"
[ -n "$METRIC" ] || die "executed metric missing; the job likely never ran"
pass "metric: $METRIC"

step "6. anonymised values"
ROW_A="$(sql "SELECT CONCAT_WS('|', username, nickname, IFNULL(email,'NULL'), IFNULL(phone,'NULL'), IFNULL(avatar_url,'NULL'), gender, status) FROM lp_ums.sys_user WHERE id=$ID_A")"
expect_eq "A username" "deleted_$ID_A" "$(printf '%s' "$ROW_A" | cut -d'|' -f1)"
expect_eq "A nickname" "已注销用户" "$(printf '%s' "$ROW_A" | cut -d'|' -f2)"
expect_eq "A email" "NULL" "$(printf '%s' "$ROW_A" | cut -d'|' -f3)"
expect_eq "A phone" "NULL" "$(printf '%s' "$ROW_A" | cut -d'|' -f4)"
expect_eq "A avatar" "NULL" "$(printf '%s' "$ROW_A" | cut -d'|' -f5)"
expect_eq "A gender" 0 "$(printf '%s' "$ROW_A" | cut -d'|' -f6)"
expect_eq "A status" 3 "$(printf '%s' "$ROW_A" | cut -d'|' -f7)"
ROW_B="$(sql "SELECT CONCAT_WS('|', deleted, IFNULL(unionid,'NULL'), IFNULL(session_key,'NULL'), openid, LENGTH(openid), REGEXP_LIKE(openid,'^anon:[0-9a-f]{64}$')) FROM lp_ums.sys_user_wechat WHERE user_id=$ID_B")"
expect_eq "B wechat deleted" 1 "$(printf '%s' "$ROW_B" | cut -d'|' -f1)"
expect_eq "B wechat unionid" "NULL" "$(printf '%s' "$ROW_B" | cut -d'|' -f2)"
expect_eq "B wechat session_key" "NULL" "$(printf '%s' "$ROW_B" | cut -d'|' -f3)"
expect_eq "B wechat openid length" 69 "$(printf '%s' "$ROW_B" | cut -d'|' -f5)"
expect_eq "B wechat openid format" 1 "$(printf '%s' "$ROW_B" | cut -d'|' -f6)"
ROW_PAY="$(sql "SELECT CONCAT_WS('|', payer_openid, LENGTH(payer_openid), REGEXP_LIKE(payer_openid,'^anon:[0-9a-f]{56}$')) FROM lp_oms.payment_record WHERE id=$PAY_ID")"
expect_eq "payment payer_openid length" 61 "$(printf '%s' "$ROW_PAY" | cut -d'|' -f2)"
expect_eq "payment payer_openid format" 1 "$(printf '%s' "$ROW_PAY" | cut -d'|' -f3)"
info "payer_openid=$(printf '%s' "$ROW_PAY" | cut -d'|' -f1)"
expect_eq "rows unchanged by execution" "$COUNT_BEFORE_EXECUTION" "$(counts)"
expect_eq "A order kept" 1 "$(sql "SELECT COUNT(*) FROM lp_oms.trade_order WHERE user_id=$ID_A")"

step "7. the deleted account is refused with 67001"
ME_A="$(api GET /api/v1/auth/me "$TOKEN_A")"
expect_eq "me A code" 67001 "$(printf '%s' "$ME_A" | json_get code)"
REFRESHED="$(api POST /api/v1/auth/refresh '' "{\"refreshToken\":\"$REFRESH_A\"}")"
expect_eq "refresh A code" 67001 "$(printf '%s' "$REFRESHED" | json_get code)"
expect_eq "me B code" 67001 "$(api GET /api/v1/auth/me "$TOKEN_B" | json_get code)"
LOGIN_AGAIN="$(api POST /api/v1/auth/login '' "{\"account\":\"$USER_A\",\"password\":\"$DEMO_PASSWORD\"}")"
info "login with the renamed account returns $(printf '%s' "$LOGIN_AGAIN" | json_get code) (password is randomised before the status gate)"
step "7c. login-path gate fixture: status 3 with unchanged credentials"
USER_C="cdg${RUN_ID}"                      # 17 chars
api POST /api/v1/auth/register '' "{\"username\":\"$USER_C\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"drill gate\"}" >/dev/null
ID_C="$(sql "SELECT id FROM lp_ums.sys_user WHERE username='$USER_C'")"
[ -n "$ID_C" ] || die "gate fixture account not found"
sql "UPDATE lp_ums.sys_user SET status = 3 WHERE id = $ID_C" >/dev/null
expect_eq "login cancelled account code" 67001 \
  "$(api POST /api/v1/auth/login '' "{\"account\":\"$USER_C\",\"password\":\"$DEMO_PASSWORD\"}" | json_get code)"

step "8. the original identifiers can be registered again"
REG_AGAIN="$(api POST /api/v1/auth/register '' "{\"username\":\"$USER_A\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"drill a again\",\"email\":\"$MAIL_A\",\"phone\":\"$PHONE_A\"}")"
expect_eq "re-register A" 0 "$(printf '%s' "$REG_AGAIN" | json_get code)"
ID_A2="$(sql "SELECT id FROM lp_ums.sys_user WHERE username='$USER_A' AND status = 1")"
[ -n "$ID_A2" ] && [ "$ID_A2" != "$ID_A" ] || die "username did not move to the new account (got [$ID_A2])"
expect_eq "username rows" 2 "$(sql "SELECT COUNT(*) FROM lp_ums.sys_user WHERE username='$USER_A'")"
pass "A re-registered as id=$ID_A2 with the same email and phone"

step "9. the same WeChat code binds a brand new account"
LOGIN_B2="$(api POST /api/v1/auth/miniapp-login '' "{\"code\":\"$CODE_B\"}")"
expect_eq "miniapp login B again" 0 "$(printf '%s' "$LOGIN_B2" | json_get code)"
ID_B2="$(printf '%s' "$LOGIN_B2" | json_get data.user.id)"
[ "$ID_B2" != "$ID_B" ] || die "wechat login reused the deleted account $ID_B"
expect_eq "live binding count" 1 \
  "$(sql "SELECT COUNT(*) FROM lp_ums.sys_user_wechat WHERE openid='mock-openid-$CODE_B' AND deleted = 0")"
expect_eq "live binding owner" "$ID_B2" \
  "$(sql "SELECT user_id FROM lp_ums.sys_user_wechat WHERE openid='mock-openid-$CODE_B' AND deleted = 0")"
expect_eq "anonymised binding kept" 1 \
  "$(sql "SELECT COUNT(*) FROM lp_ums.sys_user_wechat WHERE user_id=$ID_B AND deleted = 1 AND openid LIKE 'anon:%'")"
pass "WeChat rebind created account $ID_B2"

step "10. a repeated anonymisation rewrites nothing"
BEFORE_RETRY="$(sql "SELECT CONCAT_WS('|', payer_openid, (SELECT username FROM lp_ums.sys_user WHERE id=$ID_A)) FROM lp_oms.payment_record WHERE id=$PAY_ID")"
RETRY="$(curl -s -X POST "$TRADE_INTERNAL/internal/v1/account-deletion/anonymize" -H 'Content-Type: application/json' \
  -H "X-Internal-Token: $INTERNAL_TOKEN" -d "{\"userId\":$ID_A,\"salt\":\"$SALT\"}")"
expect_eq "retry rewritten rows" 0 "$(printf '%s' "$RETRY" | json_get data.payerOpenidRewritten)"
AFTER_RETRY="$(sql "SELECT CONCAT_WS('|', payer_openid, (SELECT username FROM lp_ums.sys_user WHERE id=$ID_A)) FROM lp_oms.payment_record WHERE id=$PAY_ID")"
expect_eq "retry left the rows untouched" "$BEFORE_RETRY" "$AFTER_RETRY"

printf '\n\033[0;32mAll account deletion checks passed.\033[0m\n'
printf 'user A=%s (deleted) -> %s (new)\nuser B=%s (deleted) -> %s (new)\norder=%s payment=%s wechat_code=%s\n' \
  "$ID_A" "$ID_A2" "$ID_B" "$ID_B2" "$ORDER_NO" "$PAY_ID" "$CODE_B"
