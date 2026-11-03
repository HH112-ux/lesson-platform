#!/usr/bin/env bash
# Shared helpers for the infra smoke checks. Sourced by each script in this directory.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Load local overrides such as LP_MYSQL_PORT=3307; only LP_* keys are exported by infra/.env.
load_env() {
  if [ -f "infra/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "infra/.env"
    set +a
  fi
}

say_pass() {
  printf 'PASS %s: %s\n' "$1" "$2"
}

say_fail() {
  printf 'FAIL %s: %s\n' "$1" "$2"
}

container_running() {
  command -v docker >/dev/null 2>&1 || return 1
  docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q 'true'
}

require_container() {
  local name="$1"
  if ! command -v docker >/dev/null 2>&1; then
    say_fail "$name" "docker CLI not found; cannot inspect the container"
    return 1
  fi
  if ! container_running "$name"; then
    say_fail "$name" "container $name is not running (docker compose -f infra/docker-compose.yml up -d)"
    return 1
  fi
  return 0
}

port_open() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -G 2 "$host" "$port" >/dev/null 2>&1
    return $?
  fi
  (exec 3<>"/dev/tcp/${host}/${port}") >/dev/null 2>&1
}
