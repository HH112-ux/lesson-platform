#!/usr/bin/env bash
# Shared helpers for the local security drills. Sourced, never executed alone.
#
# Inputs: LP_SEC_BASE_URL (default http://localhost:8080),
#         LP_SEC_MYSQL_CONTAINER (default lp-mysql),
#         LP_SEC_PASSWORD (default Demo@123456).
set -uo pipefail

SEC_BASE="${LP_SEC_BASE_URL:-http://localhost:8080}"
SEC_PASSWORD="${LP_SEC_PASSWORD:-Demo@123456}"
SEC_MYSQL_CONTAINER="${LP_SEC_MYSQL_CONTAINER:-lp-mysql}"
SEC_BODY="$(mktemp)"
trap 'rm -f "${SEC_BODY}"' EXIT

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
  python3 - "${SEC_BODY}" "$@" <<'PY'
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
  password="$(docker inspect "${SEC_MYSQL_CONTAINER}" \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p')"
  if [ -z "${password}" ]; then
    printf 'ERROR 容器 %s 没有 MYSQL_ROOT_PASSWORD，无法解析越权用例所需的 id。\n' \
      "${SEC_MYSQL_CONTAINER}" >&2
    exit 1
  fi
  docker exec -e MYSQL_PWD="${password}" "${SEC_MYSQL_CONTAINER}" mysql -uroot -N -B -e "$1"
}

login() {
  curl -s -X POST "${SEC_BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"account\":\"$1\",\"password\":\"${SEC_PASSWORD}\"}" \
    | python3 -c 'import json,sys
d = json.load(sys.stdin)
print((d.get("data") or {}).get("accessToken", ""))'
}

require_login() {
  local account=$1 token=$2
  if [ -z "${token}" ]; then
    printf 'ERROR 账号 %s 登录失败，用例无法判定，不做静默跳过。\n' "${account}" >&2
    exit 1
  fi
}

require_value() {
  if [ -z "$2" ]; then
    printf 'ERROR 缺少用例数据 %s（%s），先执行 bash scripts/seed/seed-demo.sh。\n' \
      "$1" "$3" >&2
    exit 1
  fi
}

# request METHOD PATH TOKEN BODY [extra curl args]: "-" means absent.
request() {
  local method=$1 path=$2 token=$3 body=${4:-}
  shift 4
  local args=(-s -o "${SEC_BODY}" -w '%{http_code}' -X "${method}" "${SEC_BASE}${path}")
  args+=(-H 'Content-Type: application/json')
  [ "${token}" != "-" ] && args+=(-H "Authorization: Bearer ${token}")
  [ "${body}" != "-" ] && args+=(-d "${body}")
  args+=("$@")
  HTTP="$(curl "${args[@]}")"
  CODE="$(json_field code)"
}

# raw_url URL [curl args...]: direct service, object storage and callback calls.
raw_url() {
  local url=$1
  shift
  HTTP="$(curl -s -o "${SEC_BODY}" -w '%{http_code}' "$@" "${url}")"
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

summary() {
  printf '\nSUMMARY pass=%s fail=%s\n' "${PASS}" "${FAIL}"
  [ "${FAIL}" -eq 0 ]
}
