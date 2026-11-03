#!/usr/bin/env bash
# MinIO smoke check: health endpoint on the API port.
set -uo pipefail

# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

name="minio"
port="${LP_MINIO_API_PORT:-9000}"

require_container lp-minio || exit 1

output="$(curl -fsS --max-time 5 "http://127.0.0.1:${port}/minio/health/live" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
  say_fail "$name" "health endpoint http://127.0.0.1:${port}/minio/health/live failed: ${output}"
  exit 1
fi

say_pass "$name" "health endpoint on port ${port} returned HTTP 200"
