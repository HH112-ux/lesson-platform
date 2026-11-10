#!/usr/bin/env bash
#
# Collects the six forensic items after a failed end-to-end run. Every collector is
# best effort: a broken stack must still produce whatever evidence is reachable instead
# of failing the job again.
#
# Usage: bash scripts/e2e-forensics.sh
#
# Environment:
#   LP_FORENSICS_DIR  output directory, defaults to <repo>/forensics
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${LP_FORENSICS_DIR:-$ROOT/forensics}"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/infra/.env")

mkdir -p "$OUT/service-logs" "$OUT/verify-logs"

# 1. Container state and health.
"${COMPOSE[@]}" ps -a > "$OUT/01-compose-ps.txt" 2>&1 || true

# 2. Per service logs, so a crashed service is visible without the Actions log.
for service in $("${COMPOSE[@]}" config --services 2>/dev/null); do
  "${COMPOSE[@]}" logs --no-color --tail=300 "$service" \
    > "$OUT/service-logs/$service.log" 2>&1 || true
done

# 3. The verify-all step logs, including the failing step output.
latest_logs="$(ls -dt "$ROOT"/logs/verify-* 2>/dev/null | head -1)"
if [ -n "$latest_logs" ]; then
  cp -R "$latest_logs"/. "$OUT/verify-logs/" 2>/dev/null || true
fi

# 4. Runtime probes: gateway health, container resource use and host disk.
{
  printf 'gateway health: '
  curl -sS -m 5 -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/health || true
  printf 'gateway root:   '
  curl -sS -m 5 -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/courses || true
  printf '\n--- docker stats (no stream) ---\n'
  docker stats --no-stream || true
  printf '\n--- disk ---\n'
  df -h || true
} > "$OUT/04-runtime-probes.txt" 2>&1

# 5. Business rows the suite was exercising, kept for inspection before cleanup.
"${COMPOSE[@]}" exec -T mysql mysql --default-character-set=utf8mb4 \
  -uroot -p"${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}" lp_oms > "$OUT/05-db-state.txt" 2>&1 <<'SQL' || true
SELECT COUNT(*) AS orders FROM trade_order;
SELECT COUNT(*) AS payments FROM payment_record;
SELECT COUNT(*) AS refunds FROM refund_request;
SELECT order_no, status, pay_amount, created_at FROM trade_order ORDER BY id DESC LIMIT 5;
SQL

# 6. Screenshots. This suite is API level: there is no browser session to capture.
printf 'No screenshots were captured. The end-to-end suite is API level (curl against the\n' \
  > "$OUT/06-screenshots.txt"
printf 'gateway) and never opens a browser, so there is no rendered page to screenshot.\n' \
  >> "$OUT/06-screenshots.txt"

{
  printf '# E2E forensics\n\n'
  printf 'Run: %s\n\n' "${GITHUB_RUN_ID:-local}"
  printf '| Item | File |\n|---|---|\n'
  printf '| 1. container state | 01-compose-ps.txt |\n'
  printf '| 2. service logs | service-logs/ |\n'
  printf '| 3. verify-all step logs | verify-logs/ |\n'
  printf '| 4. runtime probes | 04-runtime-probes.txt |\n'
  printf '| 5. database rows | 05-db-state.txt |\n'
  printf '| 6. screenshots | 06-screenshots.txt (none: API level suite) |\n'
} > "$OUT/SUMMARY.md"

printf 'forensics written to %s\n' "$OUT"
