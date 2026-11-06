#!/usr/bin/env bash
# Apply the demo seed dataset to the local MySQL instance.
# Usage: bash scripts/seed/seed-demo.sh
# Connection defaults follow scripts/flyway.sh (business account lp_app);
# override with LP_SEED_HOST / LP_MYSQL_PORT / LP_SEED_USER / LP_SEED_PASSWORD.
# The password is passed through MYSQL_PWD and is never printed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ENV_FILE="infra/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

DB_HOST="${LP_SEED_HOST:-127.0.0.1}"
DB_PORT="${LP_MYSQL_PORT:-3306}"
DB_USER="${LP_SEED_USER:-${LP_FLYWAY_USER:-lp_app}}"
DB_PASSWORD="${LP_SEED_PASSWORD:-${LP_FLYWAY_PASSWORD:-lp_app_dev}}"

SEED_FILE="db/seed/demo-seed.sql"

if [ ! -f "$SEED_FILE" ]; then
  printf 'FAIL seed file not found: %s\n' "$SEED_FILE" >&2
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  --protocol=TCP \
  --default-character-set=utf8mb4 \
  --batch \
  < "$SEED_FILE"

printf 'OK demo seed applied\n'
