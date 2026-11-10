#!/usr/bin/env bash
#
# AC-33 drill: the cache invalidation alert fires, then clears.
#
# Proves the whole alerting path instead of only the rule file:
#   1. real invalidation failures reach Prometheus through the auth-service counter;
#   2. the rule reports health=ok and keeps evaluating;
#   3. /api/v1/alerts and Alertmanager both show the alert firing with the counter value;
#   4. after the rate window drains, the alert returns to inactive.
#
# Failure injection uses `docker pause` on trade-service, never `stop`/`down`: a paused container
# keeps its row in the scrape targets and its port bound, so nothing else in the stack has to be
# restarted and the drill leaves no stopped service behind.  The container is paused only for the
# seconds each batch needs (until its three invalidation calls have timed out), then resumed: trade
# is shared with every other local check, so two short freezes are preferable to one long one.
#
# Why six failures and not three.  The rule is
#   sum(increase(...cache_invalidate_failed_total[5m])) >= 3   for: 5m
# A single batch of three events cannot keep that expression true for a whole five minutes: the
# oldest event leaves the range exactly when `for` is about to complete, so the pending state
# resets and the alert never fires.  The drill therefore sends two batches of three, three minutes
# apart.  The expression then holds from the first batch until the second batch ages out, which
# covers the `for: 5m` with margin and gives a stable firing window of about three minutes.
#
# Ordering follows the guard asymmetry in auth-service: applying probes trade and fails closed
# (50401) when trade is unreachable, while withdrawing does not probe at all.  Every account must
# therefore apply before the pause; only the withdrawal can generate a failure.  That is why the
# drill registers six accounts instead of reusing one twice.
#
# Usage:
#   bash scripts/observability-ac33-drill.sh
#
# Prerequisites:
#   bash scripts/dev-up.sh
#   docker compose -f infra/observability/docker-compose.yml up -d
#   trade-service must be running and healthy; the script pauses and resumes it itself.
#
# Environment:
#   LP_GATEWAY_URL   default http://localhost:8080
#   LP_AUTH_URL      default http://localhost:8101
#   LP_PROM_URL      default http://127.0.0.1:9090
#   LP_AM_URL        default http://127.0.0.1:9093
#
set -uo pipefail

BASE="${LP_GATEWAY_URL:-http://localhost:8080}"
AUTH_URL="${LP_AUTH_URL:-http://localhost:8101}"
PROM_URL="${LP_PROM_URL:-http://127.0.0.1:9090}"
AM_URL="${LP_AM_URL:-http://127.0.0.1:9093}"
ALERT="LpAuthAccountCancellationCacheInvalidateFailed"
DEMO_PASSWORD="${LP_DEMO_PASSWORD:-Demo@123456}"
RUN_ID="$(date +%d%H%M%S)"
BATCH=3
BATCH_GAP_SECONDS=180
FIRING_TIMEOUT=360
RESOLVE_TIMEOUT=420
TRADE_PAUSED=0

step() { printf '\n\033[1;34m== %s\033[0m\n' "$1"; }
pass() { printf '\033[0;32m  PASS\033[0m %s\n' "$1"; }
info() { printf '  info %s\n' "$1"; }
die() { printf '\033[0;31mABORT\033[0m %s\n' "$1" >&2; exit 1; }

pause_trade() {
  [ "$TRADE_PAUSED" = 1 ] && return 0
  docker pause lp-trade-service >/dev/null || die "cannot pause trade-service"
  TRADE_PAUSED=1
}

resume_trade() {
  if [ "$TRADE_PAUSED" = 1 ]; then
    docker unpause lp-trade-service >/dev/null 2>&1
    TRADE_PAUSED=0
    for _ in $(seq 1 30); do
      curl -sf http://127.0.0.1:8103/actuator/health >/dev/null 2>&1 && return 0
      sleep 2
    done
    printf 'WARN trade-service did not report healthy within 60s after resume\n'
  fi
}
trap resume_trade EXIT

json_get() { # dotted path; reads stdin
  python3 -c 'import json,sys
d=json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d=d[k]
print(d)' "$1"
}

api() { # method path token body
  local args=(-s -X "$1" "$BASE$2" -H 'Content-Type: application/json')
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-d "$4")
  curl "${args[@]}"
}

metric() { # invalidation failure counter, 0 when the series is absent
  local raw
  raw="$(curl -s "$AUTH_URL/actuator/prometheus" |
    grep '^lp_auth_account_cancellation_cache_invalidate_failed_total' | head -1 | awk '{print $2}')"
  printf '%s' "${raw:-0}"
}

wait_metric() { # expected-delta timeout-seconds
  local deadline=$(( $(date +%s) + $2 )) now
  while [ "$(date +%s)" -lt "$deadline" ]; do
    now="$(metric)"
    if awk -v n="$now" -v b="$BASELINE" -v e="$1" 'BEGIN { exit !(n - b >= e) }'; then
      printf '%s' "$now"
      return 0
    fi
    sleep 2
  done
  return 1
}

rule_state() { # prints "<health> <lastEvaluation>"
  curl -s "$PROM_URL/api/v1/rules?type=alert" | python3 -c '
import json,sys
name=sys.argv[1]
for g in json.load(sys.stdin)["data"]["groups"]:
    for r in g.get("rules", []):
        if r.get("name")==name:
            print(r.get("health"), r.get("lastEvaluation"))
' "$ALERT"
}

prom_alerts() { # "<state> <value> <activeAt>" for the alert, empty when absent
  curl -s "$PROM_URL/api/v1/alerts" | python3 -c '
import json,sys
name=sys.argv[1]
for a in json.load(sys.stdin)["data"]["alerts"]:
    if a["labels"].get("alertname")==name:
        print(a["state"], a.get("value","-"), a.get("activeAt","-"))
' "$ALERT"
}

am_alerts() { # one compact JSON object per alert currently known to Alertmanager
  curl -s "$AM_URL/api/v2/alerts" | python3 -c '
import json,sys
name=sys.argv[1]
for a in json.load(sys.stdin):
    labels=a.get("labels", {})
    if labels.get("alertname")!=name:
        continue
    print(json.dumps({"alertname": labels.get("alertname"),
                      "severity": labels.get("severity"),
                      "status": a.get("status", {}).get("state"),
                      "value": a.get("value", "absent-in-am-v2"),
                      "startsAt": a.get("startsAt")}, sort_keys=True))
' "$ALERT"
}

wait_prom_state() { # wanted-state timeout-seconds
  local deadline=$(( $(date +%s) + $2 )) line
  while [ "$(date +%s)" -lt "$deadline" ]; do
    line="$(prom_alerts)"
    case "$line" in
      "$1 "*) printf '%s' "$line"; return 0 ;;
    esac
    sleep 5
  done
  return 1
}

step "0. preconditions and baseline"
curl -sf "$BASE/api/v1/courses" >/dev/null || die "gateway is not serving $BASE"
curl -sf "$PROM_URL/-/healthy" >/dev/null || die "prometheus is not healthy at $PROM_URL"
curl -sf "$AM_URL/-/healthy" >/dev/null || die "alertmanager is not healthy at $AM_URL"
docker inspect lp-trade-service >/dev/null 2>&1 || die "container lp-trade-service not found"
[ "$(docker inspect -f '{{.State.Status}}' lp-trade-service)" = "running" ] ||
  die "trade-service is not running"
pass "gateway, prometheus, alertmanager and trade-service are up"
RULE="$(rule_state)"
[ -n "$RULE" ] || die "$ALERT is not loaded; mount infra/observability/prometheus-rules.yml"
info "rule before the drill: health/lastEvaluation = $RULE"
BASELINE="$(metric)"
info "cache_invalidate_failed_total before the drill: $BASELINE"
PRE_ALERTS="$(prom_alerts)"
[ -z "$PRE_ALERTS" ] || die "$ALERT is already firing from an earlier run: $PRE_ALERTS"
pass "the alert is inactive before any failure is injected"

step "1. register $((BATCH * 2)) accounts and apply while trade is still reachable"
TOKENS=()
for i in $(seq 1 $((BATCH * 2))); do
  USER_NAME="ac${RUN_ID}${i}"
  REG="$(api POST /api/v1/auth/register '' "{\"username\":\"$USER_NAME\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"ac33 $i\"}")"
  [ "$(printf '%s' "$REG" | json_get code)" = "0" ] || die "register $USER_NAME failed: $REG"
  LOGIN="$(api POST /api/v1/auth/login '' "{\"account\":\"$USER_NAME\",\"password\":\"$DEMO_PASSWORD\"}")"
  TOKEN="$(printf '%s' "$LOGIN" | json_get data.accessToken)" || die "login $USER_NAME failed: $LOGIN"
  APPLY="$(api POST /api/v1/auth/account-cancellation "$TOKEN" '{}')"
  [ "$(printf '%s' "$APPLY" | json_get code)" = "0" ] || die "apply $USER_NAME failed: $APPLY"
  TOKENS+=("$TOKEN")
done
pass "$((BATCH * 2)) accounts are in the cooling-off period; every probe saw trade reachable"

withdraw() { # index into TOKENS; the request itself succeeds, only the async invalidation fails
  local response
  response="$(api DELETE /api/v1/auth/account-cancellation "${TOKENS[$(( $1 - 1 ))]}" '')"
  [ "$(printf '%s' "$response" | json_get code)" = "0" ] || die "withdraw $1 failed: $response"
}

step "2. freeze trade, withdraw the first batch, release trade"
pause_trade
pass "trade-service paused (not stopped: the target and the port stay in place)"
withdraw 1
withdraw 2
withdraw 3
# Stay paused until every invalidation has timed out.  Resuming as soon as the last withdrawal
# returns lets queued background calls succeed against the woken container, and the counter then
# records fewer than three failures.
AFTER_BATCH_A="$(wait_metric "$BATCH" 60)" || {
  resume_trade
  die "counter did not reach +$BATCH after the first batch (now $(metric))"
}
resume_trade
pass "cache_invalidate_failed_total = $AFTER_BATCH_A (delta $(( ${AFTER_BATCH_A%.*} - ${BASELINE%.*} )))"

step "3. keep the rate alive: second batch after ${BATCH_GAP_SECONDS}s"
sleep "$BATCH_GAP_SECONDS"
pause_trade
withdraw 4
withdraw 5
withdraw 6
AFTER_BATCH_B="$(wait_metric "$((BATCH * 2))" 60)" || {
  resume_trade
  die "counter did not reach +$((BATCH * 2)) after the second batch (now $(metric))"
}
resume_trade
pass "cache_invalidate_failed_total = $AFTER_BATCH_B"

step "4. confirm the rule is still evaluated"
RULE_A="$(rule_state)"
info "rule after the second batch: $RULE_A"
sleep 20
RULE_B="$(rule_state)"
info "rule 20s later:          $RULE_B"
[ "${RULE_A%% *}" = "ok" ] && [ "${RULE_B%% *}" = "ok" ] ||
  die "the rule is not healthy: $RULE_A / $RULE_B"
[ "${RULE_A##* }" != "${RULE_B##* }" ] ||
  die "lastEvaluation did not advance ($RULE_A -> $RULE_B); the rule is not being evaluated"
pass "health=ok and lastEvaluation advances (${RULE_A##* } -> ${RULE_B##* })"

step "5. wait for the alert to fire in Prometheus"
FIRED="$(wait_prom_state firing "$FIRING_TIMEOUT")" ||
  die "the alert did not fire within ${FIRING_TIMEOUT}s; inspect $PROM_URL/alerts"
printf '  raw /api/v1/alerts: %s\n' "$FIRED"
FIRED_VALUE="$(printf '%s' "$FIRED" | awk '{print $2}')"
EXPECTED="$(awk -v a="$AFTER_BATCH_B" -v b="$BASELINE" 'BEGIN { printf "%.0f", a - b }')"
# The alert value is increase(...[5m]) at the evaluation that fired, not the cumulative counter:
# older failures have already left the range, so the value tracks the threshold (3), not the total.
awk -v v="$FIRED_VALUE" -v t="$BATCH" 'BEGIN { exit !(v + 0 >= t - 0.01) }' ||
  die "alert value $FIRED_VALUE is below the rule threshold $BATCH"
pass "prometheus reports the alert firing with value $FIRED_VALUE (threshold $BATCH, $EXPECTED failures generated)"

step "6. the same alert becomes visible in Alertmanager"
AM_FIRED=""
for _ in $(seq 1 18); do
  AM_LINE="$(am_alerts)"
  case "$AM_LINE" in
    *"\"status\": \"active\""*) AM_FIRED="$AM_LINE"; break ;;
  esac
  sleep 5
done
[ -n "$AM_FIRED" ] || die "alertmanager did not show the alert active within 90s: $(am_alerts)"
printf '  raw /api/v2/alerts: %s\n' "$AM_FIRED"
pass "alertmanager has the alert in the active state"

step "7. wait for the alert to return to inactive"
info "the last failure leaves the 5m range 5m after batch B; the alert resolves after that"
# Resolved alerts disappear from /api/v1/alerts instead of turning into an inactive entry, so stop
# on either an inactive state or an empty result; waiting only for "inactive" burns the full timeout.
DEADLINE=$(( $(date +%s) + RESOLVE_TIMEOUT ))
FINAL=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  FINAL="$(prom_alerts)"
  case "$FINAL" in
    inactive\ *|"") break ;;
  esac
  sleep 5
done
case "$FINAL" in
  inactive\ *)
    printf '  raw /api/v1/alerts: %s\n' "$FINAL"
    pass "prometheus reports the alert inactive"
    ;;
  "")
    pass "prometheus no longer lists the alert (no active series)"
    ;;
  *)
    die "the alert is still active after ${RESOLVE_TIMEOUT}s: $FINAL"
    ;;
esac
info "alertmanager after resolution: $(am_alerts)"

printf '\n\033[0;32mAC-33 drill passed.\033[0m\n'
printf 'counter: baseline %s -> %s (generated %s failures in two batches)\n' \
  "$BASELINE" "$AFTER_BATCH_B" "$EXPECTED"
printf 'rule: health=ok, lastEvaluation advanced %s -> %s\n' "${RULE_A##* }" "${RULE_B##* }"
printf 'firing value: %s\n' "$FIRED_VALUE"
