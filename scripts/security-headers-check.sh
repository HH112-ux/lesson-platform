#!/usr/bin/env bash
# Checks the browser facing security response headers of the local stack.
#
# The web app is served by nginx and the API by the gateway. This only reports
# facts: a missing header is a FAIL. The values are configured in
# docker/nginx.conf; the gateway keeps its own response headers untouched.
#
# Usage: bash scripts/security-headers-check.sh
# Inputs: LP_SEC_WEB_URL (default http://localhost:5173),
#         LP_SEC_BASE_URL (default http://localhost:8080).
set -uo pipefail

WEB="${LP_SEC_WEB_URL:-http://localhost:5173}"
BASE="${LP_SEC_BASE_URL:-http://localhost:8080}"

PASS=0
FAIL=0

report() {
  local label=$1 value=$2
  if [ -n "${value}" ]; then
    PASS=$((PASS + 1))
    printf 'PASS  %s | %s\n' "${label}" "${value}"
  else
    FAIL=$((FAIL + 1))
    printf 'FAIL  %s | header absent\n' "${label}"
  fi
}

if ! curl -fsS -o /dev/null "${WEB}/"; then
  printf 'ERROR 打不开 %s/，先执行 bash scripts/dev-up.sh。\n' "${WEB}" >&2
  exit 1
fi

header_of() {
  curl -sI "$1" | tr -d '\r' | awk -v name="$2" '
    BEGIN { IGNORECASE = 1 }
    index(tolower($0), tolower(name) ":") == 1 {
      sub(/^[^:]*:[[:space:]]*/, "", $0)
      print $0
      exit
    }'
}

for target in "web:${WEB}/" "api:${WEB}/api/v1/courses?size=1"; do
  name="${target%%:*}"
  url="${target#*:}"
  report "${name} X-Content-Type-Options" "$(header_of "${url}" 'X-Content-Type-Options')"
  report "${name} X-Frame-Options" "$(header_of "${url}" 'X-Frame-Options')"
done

report 'web Content-Security-Policy' "$(header_of "${WEB}/" 'Content-Security-Policy')"
report 'web Referrer-Policy' "$(header_of "${WEB}/" 'Referrer-Policy')"

SERVER="$(header_of "${WEB}/" 'Server')"
if printf '%s' "${SERVER}" | grep -qE '/[0-9]'; then
  FAIL=$((FAIL + 1))
  printf 'FAIL  server version disclosure | Server: %s\n' "${SERVER}"
else
  PASS=$((PASS + 1))
  printf 'PASS  server version disclosure | Server: %s\n' "${SERVER:-absent}"
fi

printf '\nSUMMARY pass=%s fail=%s\n' "${PASS}" "${FAIL}"
[ "${FAIL}" -eq 0 ]
