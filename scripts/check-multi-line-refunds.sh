#!/usr/bin/env bash
#
# Pre-revert guard for combined order refunds.
#
# The combined order changes grant access for every line when a payment succeeds and revoke every
# line when a refund settles. Reverting them while a multi-line order is still waiting for or
# processing a refund would strand access for the remaining courses, so rollback must wait until
# every such order is settled.
#
# Usage:
#   bash scripts/check-multi-line-refunds.sh
#
# Exit codes:
#   0  no blocking order, rollback is safe
#   1  blocking orders found, rollback is forbidden and the list is printed
#   2  the trade database could not be inspected
#
# Connection settings follow scripts/refund-drill.sh. Override the compose service name with
# LP_GUARD_MYSQL_SERVICE and the root password with LP_MYSQL_ROOT_PASSWORD.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/infra/.env")
MYSQL_ROOT_PASSWORD="${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}"
MYSQL_SERVICE="${LP_GUARD_MYSQL_SERVICE:-mysql}"

read -r -d '' SQL <<'EOF'
SELECT o.order_no,
       o.status,
       COUNT(i.id) AS line_count,
       GROUP_CONCAT(i.course_id ORDER BY i.course_id) AS course_ids,
       COALESCE(r.status, -1) AS refund_status
FROM lp_oms.trade_order o
JOIN lp_oms.trade_order_item i ON i.order_id = o.id
LEFT JOIN lp_oms.refund_request r ON r.order_id = o.id AND r.status IN (0, 1, 5)
WHERE o.status = 4 OR r.id IS NOT NULL
GROUP BY o.id, o.order_no, o.status, r.status
HAVING COUNT(i.id) > 1
EOF

OUTPUT="$(mktemp)"
ERRORS="$(mktemp)"
trap 'rm -f "$OUTPUT" "$ERRORS"' EXIT

if ! "${COMPOSE[@]}" exec -T "$MYSQL_SERVICE" \
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "$SQL" >"$OUTPUT" 2>"$ERRORS"; then
  echo "FAIL cannot inspect the trade database; start scripts/dev-up.sh or set LP_GUARD_MYSQL_SERVICE" >&2
  cat "$ERRORS" >&2
  exit 2
fi

if [ -s "$OUTPUT" ]; then
  echo "ROLLBACK FORBIDDEN: multi-line orders are still waiting for or processing a refund."
  echo "Settle or reconcile them first, then run this check again."
  printf '%-26s %-8s %-6s %-24s %s\n' ORDER_NO ORDER_STATE LINES COURSE_IDS REFUND_STATE
  while IFS=$'\t' read -r orderNo orderState lineCount courseIds refundState; do
    printf '%-26s %-8s %-6s %-24s %s\n' \
      "$orderNo" "$orderState" "$lineCount" "$courseIds" "$refundState"
  done <"$OUTPUT"
  exit 1
fi

echo "OK no multi-line order is waiting for or processing a refund; rollback is safe."
