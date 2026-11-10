#!/usr/bin/env bash
#
# Replica race drill: two auth-service instances execute one cancellation backlog exactly once.
#
# The execution job claims a request with
#   UPDATE user_account_cancellation SET status = 2, executed_at = ? WHERE id = ? AND status = 0
# so only the transaction that observes status = 0 can win.  This drill proves that at runtime with
# two real replicas instead of a stubbed mapper:
#   1. five due rows are seeded with pending status;
#   2. two extra auth-service containers scan the same database at the same interval;
#   3. every row ends in status = 2 with executed_at set;
#   4. the executed counters of the two replicas sum to five;
#   5. no requestId is reported executed twice, and the losers report already-claimed or
#      status-changed instead of executing.
#
# Both replicas are started with `docker compose run`, which publishes no host port, so the
# container that already owns 127.0.0.1:8101 keeps serving.  The primary instance must not scan
# during the drill: the script pauses it when the job is enabled there and resumes it on exit.
#
# Usage:
#   bash scripts/auth-replica-race-drill.sh
#
# Prerequisites:
#   bash scripts/dev-up.sh   (mysql, redis, trade-service and the auth image must exist)
#
# Environment:
#   LP_JWT_DIR    directory holding jwt-private.pem, default <repo>/infra/jwt; point it at the main
#                 checkout when the script runs from a worktree without the ignored key
#   LP_SALT       anonymisation salt for the drill replicas, read from the primary container when
#                 it is set there, otherwise a drill-only value is used
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_CONTAINER="${LP_MYSQL_CONTAINER:-lp-mysql}"
MYSQL_USER="${LP_MYSQL_USER:-root}"
MYSQL_PASSWORD="${LP_MYSQL_PASSWORD:-lp_root_dev}"
PRIMARY="${LP_AUTH_CONTAINER:-lp-auth-service}"
REPLICA_A="lp-auth-race-a"
REPLICA_B="lp-auth-race-b"
ROWS=5
RUN_ID="$(date +%d%H%M%S)"
SCAN_DELAY_MS=5000
SCAN_INITIAL_DELAY_MS=5000
PRIMARY_PAUSED=0
OVERRIDE_FILE=""

step() { printf '\n\033[1;34m== %s\033[0m\n' "$1"; }
pass() { printf '\033[0;32m  PASS\033[0m %s\n' "$1"; }
info() { printf '  info %s\n' "$1"; }
die() { printf '\033[0;31mABORT\033[0m %s\n' "$1" >&2; exit 1; }

sql() { # prints rows without headers
  docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D lp_ums -N -B -e "$1" \
    2>/dev/null
}

cleanup() {
  if [ "$PRIMARY_PAUSED" = 1 ]; then
    docker unpause "$PRIMARY" >/dev/null 2>&1
    PRIMARY_PAUSED=0
  fi
  docker rm -f "$REPLICA_A" "$REPLICA_B" >/dev/null 2>&1
  [ -n "$OVERRIDE_FILE" ] && rm -f "$OVERRIDE_FILE"
}
trap cleanup EXIT

replica_metric_sum() { # container metric-name
  docker exec "$1" bash -c \
    "exec 3<>/dev/tcp/127.0.0.1/8101; printf 'GET /actuator/prometheus HTTP/1.0\r\n\r\n' >&3; cat <&3" \
    2>/dev/null | tr -d '\r' |
    awk -v m="$2" 'index($1, m) == 1 { sum += $2 } END { printf "%.0f", sum + 0 }'
}

replica_executed_request_ids() { # container; requestId of every executed log line
  docker logs "$1" 2>&1 | grep 'account cancellation executed:' |
    sed -n 's/.*requestId=\([0-9]*\).*/\1/p' | sort -n
}

step "0. preconditions"
docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1 || die "$MYSQL_CONTAINER is not running"
docker inspect "$PRIMARY" >/dev/null 2>&1 || die "$PRIMARY is not running"
JWT_DIR="${LP_JWT_DIR:-$ROOT/infra/jwt}"
[ -f "$JWT_DIR/jwt-private.pem" ] ||
  die "missing $JWT_DIR/jwt-private.pem; set LP_JWT_DIR to a checkout that has infra/jwt"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")
[ -f "$ROOT/infra/.env" ] && COMPOSE+=(--env-file "$ROOT/infra/.env")
# The replicas must join the network of the running stack (normally lp-dev_default).  The project
# name is read from the primary container instead of being assumed, because the stack may have been
# started from another checkout; without -p, compose would invent a project from this worktree.
PROJECT="$(docker inspect "$PRIMARY" -f '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null)"
[ -n "$PROJECT" ] || die "cannot read the compose project of $PRIMARY"
COMPOSE+=(-p "$PROJECT")
if [ "$JWT_DIR" != "$ROOT/infra/jwt" ]; then
  OVERRIDE_FILE="$(mktemp -t lp-race-jwt)"
  printf 'services:\n  auth-service:\n    volumes:\n      - %s:/run/jwt:ro\n' "$JWT_DIR" >"$OVERRIDE_FILE"
  COMPOSE+=(-f "$OVERRIDE_FILE")
fi
SALT="${LP_SALT:-$(docker exec "$PRIMARY" printenv LP_ANONYMIZE_SALT 2>/dev/null || true)}"
[ -n "$SALT" ] || SALT="lp-auth-race-drill-${RUN_ID}"
PRIMARY_JOB="$(docker exec "$PRIMARY" printenv LP_AUTH_ACCOUNT_DELETION_EXECUTION_ENABLED 2>/dev/null || true)"
info "primary execution job enabled: ${PRIMARY_JOB:-unset}"
pass "mysql, primary auth and the JWT key are available"

step "1. start two replicas with the execution job enabled"
if [ "$PRIMARY_JOB" = "true" ]; then
  docker pause "$PRIMARY" >/dev/null || die "cannot pause $PRIMARY"
  PRIMARY_PAUSED=1
  info "primary paused so exactly two scanners run"
fi
start_replica() { # name
  "${COMPOSE[@]}" run -d --no-deps --name "$1" \
    -e LP_AUTH_ACCOUNT_DELETION_EXECUTION_ENABLED=true \
    -e LP_ANONYMIZE_SALT="$SALT" \
    -e LP_AUTH_ACCOUNT_DELETION_SCAN_DELAY_MS="$SCAN_DELAY_MS" \
    -e LP_AUTH_ACCOUNT_DELETION_SCAN_INITIAL_DELAY_MS="$SCAN_INITIAL_DELAY_MS" \
    auth-service >/dev/null 2>&1 || die "cannot start $1"
  for _ in $(seq 1 60); do
    docker exec "$1" bash -c 'echo > /dev/tcp/127.0.0.1/8101' >/dev/null 2>&1 && return 0
    sleep 2
  done
  docker logs "$1" 2>&1 | tail -20
  die "$1 did not open 8101 within 120s"
}
start_replica "$REPLICA_A"
pass "$REPLICA_A is listening (scan every ${SCAN_DELAY_MS}ms)"
start_replica "$REPLICA_B"
pass "$REPLICA_B is listening (scan every ${SCAN_DELAY_MS}ms)"

step "2. pause both replicas, seed $ROWS due rows, release both at the same instant"
# Pausing freezes the scan threads while the scheduler clock keeps running, so both timers are
# overdue after the pause and the two replicas hit the same rows within milliseconds of the
# release.  Without this the replicas scan seconds apart and the drill would prove nothing.
docker pause "$REPLICA_A" "$REPLICA_B" >/dev/null || die "cannot pause the replicas"
STALE="$(sql "DELETE c FROM user_account_cancellation c
              JOIN sys_user u ON u.id = c.user_id
              WHERE u.username LIKE 'race%' AND u.nickname LIKE 'race %';
              SELECT ROW_COUNT()")"
[ "${STALE:-0}" = "0" ] || info "removed $STALE stale drill rows from an interrupted run"
sql "DELETE FROM sys_user WHERE username LIKE 'race%' AND nickname LIKE 'race %'" >/dev/null
for i in $(seq 1 "$ROWS"); do
  sql "INSERT INTO sys_user (username, password_hash, nickname, status)
       SELECT 'race${RUN_ID}${i}', password_hash, 'race ${RUN_ID} ${i}', 2
       FROM sys_user WHERE username = 'demo_buyer'" >/dev/null ||
    die "cannot insert drill user $i"
done
sql "INSERT INTO user_account_cancellation (user_id, status, requested_at, effective_at)
     SELECT id, 0, NOW(3) - INTERVAL 8 DAY, NOW(3) - INTERVAL 5 MINUTE
     FROM sys_user WHERE username LIKE 'race${RUN_ID}%'"
USER_IDS="$(sql "SELECT id FROM sys_user WHERE username LIKE 'race${RUN_ID}%' ORDER BY id" | paste -sd, -)"
[ "$(printf '%s' "$USER_IDS" | tr -cd ',' | wc -c | tr -d ' ')" = "$((ROWS - 1))" ] ||
  die "expected $ROWS drill users, got [$USER_IDS]"
DUE="$(sql "SELECT COUNT(*) FROM user_account_cancellation WHERE user_id IN ($USER_IDS) AND status = 0")"
[ "$DUE" = "$ROWS" ] || die "expected $ROWS pending rows, found $DUE"
info "users: $USER_IDS"
info "pending rows: $DUE"
sleep 6
docker unpause "$REPLICA_A" "$REPLICA_B" >/dev/null || die "cannot release the replicas"
pass "both replicas resumed on $DUE pending rows at the same moment"

step "3. wait for the backlog to drain"
DRAINED=""
for _ in $(seq 1 90); do
  DRAINED="$(sql "SELECT COUNT(*) FROM lp_ums.user_account_cancellation
                  WHERE user_id IN ($USER_IDS) AND status = 2 AND executed_at IS NOT NULL")"
  [ "$DRAINED" = "$ROWS" ] && break
  sleep 2
done
[ "$DRAINED" = "$ROWS" ] || {
  sql "SELECT id, user_id, status, executed_at FROM lp_ums.user_account_cancellation
       WHERE user_id IN ($USER_IDS) ORDER BY id"
  die "only $DRAINED of $ROWS rows reached status = 2"
}
printf '  raw rows (id, user_id, status, executed_at):\n'
sql "SELECT id, user_id, status, executed_at FROM lp_ums.user_account_cancellation
     WHERE user_id IN ($USER_IDS) ORDER BY id" | sed 's/^/    /'
pass "all $ROWS rows are status = 2 with executed_at set"

step "4. executed counters of the two replicas"
EXEC_A="$(replica_metric_sum "$REPLICA_A" lp_auth_account_cancellation_executed_total)"
EXEC_B="$(replica_metric_sum "$REPLICA_B" lp_auth_account_cancellation_executed_total)"
SKIP_A="$(replica_metric_sum "$REPLICA_A" lp_auth_account_cancellation_execute_skipped_total)"
SKIP_B="$(replica_metric_sum "$REPLICA_B" lp_auth_account_cancellation_execute_skipped_total)"
printf '  %-16s executed=%-3s skipped=%-3s\n' "$REPLICA_A" "$EXEC_A" "$SKIP_A"
printf '  %-16s executed=%-3s skipped=%-3s\n' "$REPLICA_B" "$EXEC_B" "$SKIP_B"
[ $((EXEC_A + EXEC_B)) -eq "$ROWS" ] ||
  die "executed counters sum to $((EXEC_A + EXEC_B)), expected $ROWS"
pass "executed counters sum to $ROWS ($EXEC_A + $EXEC_B)"

step "5. no requestId is executed twice"
IDS_A="$(replica_executed_request_ids "$REPLICA_A")"
IDS_B="$(replica_executed_request_ids "$REPLICA_B")"
ALL_IDS="$(printf '%s\n%s\n' "$IDS_A" "$IDS_B" | sed '/^$/d' | sort -n)"
TOTAL_LINES="$(printf '%s\n' "$ALL_IDS" | sed '/^$/d' | wc -l | tr -d ' ')"
UNIQUE_IDS="$(printf '%s\n' "$ALL_IDS" | sed '/^$/d' | sort -u | wc -l | tr -d ' ')"
DUPLICATES="$(printf '%s\n' "$ALL_IDS" | sed '/^$/d' | sort -n | uniq -d | paste -sd, -)"
printf '  %s executed requestIds: %s\n' "$REPLICA_A" "$(printf '%s' "$IDS_A" | paste -sd, -)"
printf '  %s executed requestIds: %s\n' "$REPLICA_B" "$(printf '%s' "$IDS_B" | paste -sd, -)"
[ "$TOTAL_LINES" -eq "$ROWS" ] || die "logged executed lines: $TOTAL_LINES, expected $ROWS"
[ -z "$DUPLICATES" ] || die "requestIds executed twice: $DUPLICATES"
pass "$TOTAL_LINES executed log lines, $UNIQUE_IDS unique requestIds, no duplicates"

step "6. losers skip instead of executing"
printf '  skipped lines:\n'
docker logs "$REPLICA_A" 2>&1 | grep 'account cancellation skipped:' | sed 's/^/    A /' || true
docker logs "$REPLICA_B" 2>&1 | grep 'account cancellation skipped:' | sed 's/^/    B /' || true
printf '  failed lines:\n'
FAILED="$(printf '%s\n%s\n' \
  "$(docker logs "$REPLICA_A" 2>&1 | grep -c 'account cancellation failed:' || true)" \
  "$(docker logs "$REPLICA_B" 2>&1 | grep -c 'account cancellation failed:' || true)" |
  awk '{s += $1} END {print s + 0}')"
printf '    total %s\n' "$FAILED"
[ "$FAILED" -eq 0 ] || die "$FAILED execution(s) failed; inspect the logs above"
pass "no execution failed; contended rows were reported as skipped"

printf '\n\033[0;32mReplica race drill passed.\033[0m\n'
printf 'rows: %s, executed: %s + %s, unique requestIds: %s\n' \
  "$ROWS" "$EXEC_A" "$EXEC_B" "$UNIQUE_IDS"
printf 'replicas: %s, %s (removed on exit)\n' "$REPLICA_A" "$REPLICA_B"
