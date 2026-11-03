#!/usr/bin/env bash
# Flyway wrapper for the seven business databases.
# Usage: bash scripts/flyway.sh <command> <target>
#   command: migrate | info | validate | repair
#   target:  all | one database (lp_ums lp_cms lp_oms lp_lms lp_ugc lp_ai lp_ops)
#            or one migration directory (auth course trade learn ugc ai ops)
# Database credentials can be overridden with LP_FLYWAY_USER / LP_FLYWAY_PASSWORD.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ENV_FILE="infra/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

DB_HOST="${LP_FLYWAY_HOST:-127.0.0.1}"
DB_PORT="${LP_MYSQL_PORT:-3306}"
DB_USER="${LP_FLYWAY_USER:-lp_app}"
DB_PASSWORD="${LP_FLYWAY_PASSWORD:-lp_app_dev}"

usage() {
  cat <<'USAGE'
Usage: bash scripts/flyway.sh <command> <target>

  command: migrate | info | validate | repair
  target:  all | lp_ums | lp_cms | lp_oms | lp_lms | lp_ugc | lp_ai | lp_ops
           | auth | course | trade | learn | ugc | ai | ops

Database to migration directory mapping:
  lp_ums -> auth    lp_cms -> course   lp_oms -> trade
  lp_lms -> learn   lp_ugc -> ugc      lp_ai  -> ai
  lp_ops -> ops

The admin domain owns the dedicated lp_ops database (engineering plan 15.2 D13),
so admin-service no longer shares schema history with lp_ums and lp_cms.
USAGE
}

command="$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')"
target="$(printf '%s' "${2:-}" | tr '[:upper:]' '[:lower:]')"

if [ -z "$command" ] || [ -z "$target" ]; then
  usage
  exit 1
fi

case "$command" in
  migrate|info|validate|repair) ;;
  *)
    printf 'FAIL unsupported command: %s\n\n' "$command"
    usage
    exit 1
    ;;
esac

if ! command -v flyway >/dev/null 2>&1; then
  printf 'FAIL flyway CLI not found in PATH\n'
  exit 1
fi

resolve_db() {
  case "$1" in
    lp_ums|auth)   printf 'lp_ums auth\n' ;;
    lp_cms|course) printf 'lp_cms course\n' ;;
    lp_oms|trade)  printf 'lp_oms trade\n' ;;
    lp_lms|learn)  printf 'lp_lms learn\n' ;;
    lp_ugc|ugc)    printf 'lp_ugc ugc\n' ;;
    lp_ai|ai)      printf 'lp_ai ai\n' ;;
    lp_ops|ops)    printf 'lp_ops ops\n' ;;
    *) return 1 ;;
  esac
}

run_one() {
  local db="$1" dir="$2"
  local location="db/migration/$dir"

  if [ ! -d "$location" ]; then
    printf 'FAIL migration directory not found: %s\n' "$location"
    return 1
  fi

  printf '==> %s %s (%s)\n' "$command" "$db" "$location"
  flyway \
    -url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${db}?useSSL=false&allowPublicKeyRetrieval=true" \
    -user="$DB_USER" \
    -password="$DB_PASSWORD" \
    -locations="filesystem:${location}" \
    "$command"
}

if [ "$target" = "all" ]; then
  for db in lp_ums lp_cms lp_oms lp_lms lp_ugc lp_ai lp_ops; do
    resolved="$(resolve_db "$db")" || { printf 'FAIL unresolved database: %s\n' "$db"; exit 1; }
    # shellcheck disable=SC2086
    run_one $resolved
  done
  printf 'OK flyway %s finished for all seven databases\n' "$command"
  exit 0
fi

if ! resolved="$(resolve_db "$target")"; then
  printf 'FAIL unknown target: %s\n\n' "$target"
  usage
  exit 1
fi

# shellcheck disable=SC2086
run_one $resolved
printf 'OK flyway %s finished for %s\n' "$command" "$target"
