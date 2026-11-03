#!/usr/bin/env bash
# Nacos smoke check: readiness endpoint.
set -uo pipefail

# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

name="nacos"
port="${LP_NACOS_PORT:-8848}"

require_container lp-nacos || exit 1

url="http://127.0.0.1:${port}/nacos/v1/console/health/readiness"
output="$(curl -fsS --max-time 5 "$url" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
  say_fail "$name" "readiness endpoint ${url} failed: ${output}"
  exit 1
fi

say_pass "$name" "readiness endpoint on port ${port} returned HTTP 200"
