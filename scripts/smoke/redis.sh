#!/usr/bin/env bash
# Redis smoke check: PING the container instance.
set -uo pipefail

# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

name="redis"
port="${LP_REDIS_PORT:-6379}"

require_container lp-redis || exit 1

output="$(docker exec lp-redis redis-cli ping 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
  say_fail "$name" "redis-cli ping inside lp-redis failed: ${output}"
  exit 1
fi

if [ "$output" != "PONG" ]; then
  say_fail "$name" "unexpected PING reply: ${output}"
  exit 1
fi

if ! port_open 127.0.0.1 "$port"; then
  say_fail "$name" "host port 127.0.0.1:${port} is not reachable; check LP_REDIS_PORT and port conflicts"
  exit 1
fi

say_pass "$name" "PING returned PONG, host port ${port} reachable"
