#!/usr/bin/env bash
#
# AC-33 drill: the cache invalidation alert fires, then clears.
#
# Proves the whole alerting path instead of only the rule file:
#   1. real invalidation failures reach Prometheus through the auth-service counter;
#   2. the rule reports health=ok and keeps evaluating;
#   3. /api/v1/alerts shows the alert firing with the expression value and Alertmanager shows it
#      active (Alertmanager carries no numeric value);
#   4. after the rate window drains, the alert returns to inactive.
#
# Failure injection uses `docker pause` on trade-service, never `stop`/`down`: a paused container
# keeps its row in the scrape targets and its port bound, so nothing else in the stack has to be
# restarted and the drill leaves no stopped service behind.  Trade is frozen only until the three
# invalidation calls have timed out, then resumed: it is shared with every other local check.
#
# The rule is
#   sum(increase(...cache_invalidate_failed_total[5m])) >= 3   for: 5m
# `for` is as long as the range itself, so one burst can never fire: three events at t=0 keep the
# expression true only until t=5m, exactly when pending would complete, then the oldest event leaves
# the range and pending re-arms from zero.  The condition has to stay true for a full five minutes,
# which is also what the rule means in production: a sustained invalidation outage, not one blip.
# The drill therefore injects two batches of three failures three minutes apart.  Batch one starts
# pending at t=0; batch two refreshes the window at t=3m, so the expression stays >= 3 until t=8m
# and pending completes at t=5m.  Never shorten `for` to make the drill cheaper: the debounce is a
# product threshold and the drill adapts to the rule, not the other way round.
#
# Ordering follows the guard asymmetry in auth-service: applying probes trade and fails closed
# (50401) when trade is unreachable, while withdrawing does not probe at all.  Every account must
# therefore apply before the pause; only the withdrawal can generate a failure.
#
# One more trap: Micrometer registers the counter on first increment, so a freshly started
# auth-service exposes no series to Prometheus at all.  A series that first appears at 3 is invisible
# to increase() -- there is no older sample to subtract -- so batch 1 alone would leave the
# expression at 0.  When that is the case the script spends one extra account on a warm-up failure
# and waits for two scrapes before the measured batches start.
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
BATCHES=2
GAP=180
FIRING_TIMEOUT=420
RESOLVE_TIMEOUT=420
COUNTER=lp_auth_account_cancellation_cache_invalidate_failed_total
NEED_WARMUP=0
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

series_samples() { # scraped samples of the counter inside the last 2m, 0 when the series is absent
  curl -s --get "$PROM_URL/api/v1/query" \
    --data-urlencode "query=count_over_time($COUNTER[2m])" |
    python3 -c 'import json,sys
r=json.load(sys.stdin)["data"]["result"]
print(r[0]["value"][1] if r else "0")' 2>/dev/null
}

wait_metric_at() { # absolute-counter-target timeout-seconds
  local deadline=$(( $(date +%s) + $2 )) now
  while [ "$(date +%s)" -lt "$deadline" ]; do
    now="$(metric)"
    if awk -v n="$now" -v t="$1" 'BEGIN { exit !(n + 0 >= t + 0) }'; then
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
SAMPLES="$(series_samples)"
if [ "${SAMPLES:-0}" -lt 2 ] 2>/dev/null; then
  NEED_WARMUP=1
fi
ACCOUNTS=$(( BATCH * BATCHES + NEED_WARMUP ))
info "counter samples in prometheus over 2m: ${SAMPLES:-0}; warm-up account: $NEED_WARMUP"

step "1. register $ACCOUNTS accounts and apply while trade is still reachable"
TOKENS=()
for i in $(seq 1 "$ACCOUNTS"); do
  USER_NAME="ac${RUN_ID}${i}"
  REG="$(api POST /api/v1/auth/register '' "{\"username\":\"$USER_NAME\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"ac33 $i\"}")"
  [ "$(printf '%s' "$REG" | json_get code)" = "0" ] || die "register $USER_NAME failed: $REG"
  LOGIN="$(api POST /api/v1/auth/login '' "{\"account\":\"$USER_NAME\",\"password\":\"$DEMO_PASSWORD\"}")"
  TOKEN="$(printf '%s' "$LOGIN" | json_get data.accessToken)" || die "login $USER_NAME failed: $LOGIN"
  APPLY="$(api POST /api/v1/auth/account-cancellation "$TOKEN" '{}')"
  [ "$(printf '%s' "$APPLY" | json_get code)" = "0" ] || die "apply $USER_NAME failed: $APPLY"
  TOKENS+=("$TOKEN")
done
pass "$ACCOUNTS accounts are in the cooling-off period; every probe saw trade reachable"

withdraw() { # index into TOKENS; the request itself succeeds, only the async invalidation fails
  local response
  response="$(api DELETE /api/v1/auth/account-cancellation "${TOKENS[$(( $1 - 1 ))]}" '')"
  [ "$(printf '%s' "$response" | json_get code)" = "0" ] || die "withdraw $1 failed: $response"
}

run_batch() { # label first-index count delta-from-baseline
  local label="$1" i target now first last
  first=$2
  last=$(( $2 + $3 - 1 ))
  target="$(awk -v b="$BASELINE" -v e="$4" 'BEGIN { printf "%.0f", b + e }')"
  pause_trade
  pass "trade-service paused for $label (paused, not stopped: target and port stay in place)"
  for i in $(seq "$first" "$last"); do withdraw "$i"; done
  # Stay paused until every invalidation has timed out.  Resuming as soon as the last withdrawal
  # returns lets queued background calls succeed against the woken container, and the counter then
  # records fewer failures than the batch size.
  now="$(wait_metric_at "$target" 90)" || {
    resume_trade
    die "$label: counter did not reach $target (now $(metric))"
  }
  resume_trade
  pass "$label: cache_invalidate_failed_total = $now (target $target)"
}

step "2. warm up the counter series so increase() can see batch 1"
if [ "$NEED_WARMUP" = 1 ]; then
  run_batch "warm-up" 1 1 1
  info "waiting for two scrapes of $COUNTER"
  for _ in $(seq 1 24); do
    [ "$(series_samples)" -ge 2 ] 2>/dev/null && break
    sleep 5
  done
  [ "$(series_samples)" -ge 2 ] 2>/dev/null ||
    die "prometheus still holds fewer than two samples of $COUNTER; increase() cannot work"
  pass "the counter is scraped; the next increase is measurable"
else
  pass "the counter series is already scraped; no warm-up failure needed"
fi
BASELINE="$(metric)"
info "cache_invalidate_failed_total at baseline: $BASELINE"
FIRST=$(( 1 + NEED_WARMUP ))

step "3. batch 1: freeze trade, withdraw accounts $FIRST-$(( FIRST + BATCH - 1 )), release trade"
run_batch "batch 1" "$FIRST" "$BATCH" "$BATCH"
BATCH1_AT="$(date +%s)"

step "4. hold ${GAP}s so batch 2 lands inside batch 1's 5m range"
# Sampling here is the evidence that the expression stays >= 3 across the gap instead of draining:
# an empty or dropped result would mean pending had re-armed before batch 2.
while :; do
  REMAIN=$(( BATCH1_AT + GAP - $(date +%s) ))
  [ "$REMAIN" -le 0 ] && break
  info "t+$(( $(date +%s) - BATCH1_AT ))s: $(prom_alerts)"
  [ "$REMAIN" -gt 45 ] && REMAIN=45
  sleep "$REMAIN"
done
pass "batch 1 is still inside the 5m range at t+${GAP}s"

step "5. batch 2: freeze trade again, withdraw the last $BATCH accounts, release trade"
run_batch "batch 2" $(( FIRST + BATCH )) "$BATCH" $(( BATCH * BATCHES ))
AFTER_BATCH="$(metric)"

step "6. confirm the rule is still evaluated"
RULE_A="$(rule_state)"
info "rule after the failures: $RULE_A"
sleep 20
RULE_B="$(rule_state)"
info "rule 20s later:          $RULE_B"
[ "${RULE_A%% *}" = "ok" ] && [ "${RULE_B%% *}" = "ok" ] ||
  die "the rule is not healthy: $RULE_A / $RULE_B"
[ "${RULE_A##* }" != "${RULE_B##* }" ] ||
  die "lastEvaluation did not advance ($RULE_A -> $RULE_B); the rule is not being evaluated"
pass "health=ok and lastEvaluation advances (${RULE_A##* } -> ${RULE_B##* })"

step "7. wait for the alert to fire in Prometheus"
# Sample while pending: a stable activeAt is the proof that the expression never dropped below 3
# during the whole 5m hold.  If pending re-arms, activeAt jumps forward and the run is invalid.
PENDING_ACTIVE_AT=""
FIRED=""
FIRED_ELAPSED=""
DEADLINE=$(( $(date +%s) + FIRING_TIMEOUT ))
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  LINE="$(prom_alerts)"
  ELAPSED=$(( $(date +%s) - BATCH1_AT ))
  case "$LINE" in
    firing\ *)
      printf '  t+%ss firing:  %s\n' "$ELAPSED" "$LINE"
      FIRED="$LINE"
      FIRED_ELAPSED="$ELAPSED"
      break
      ;;
    pending\ *)
      ACTIVE_AT="$(printf '%s' "$LINE" | awk '{print $3}')"
      if [ -z "$PENDING_ACTIVE_AT" ]; then
        PENDING_ACTIVE_AT="$ACTIVE_AT"
      elif [ "$ACTIVE_AT" != "$PENDING_ACTIVE_AT" ]; then
        die "pending re-armed (activeAt $PENDING_ACTIVE_AT -> $ACTIVE_AT); the condition was not continuously true"
      fi
      printf '  t+%ss pending: %s\n' "$ELAPSED" "$LINE"
      ;;
    *)
      printf '  t+%ss %s\n' "$ELAPSED" "${LINE:-no active series}"
      ;;
  esac
  sleep 15
done
[ -n "$FIRED" ] || die "the alert did not fire within ${FIRING_TIMEOUT}s; inspect $PROM_URL/alerts"
printf '  raw /api/v1/alerts: %s\n' "$FIRED"
FIRED_VALUE="$(printf '%s' "$FIRED" | awk '{print $2}')"
EXPECTED="$(awk -v a="$AFTER_BATCH" -v b="$BASELINE" 'BEGIN { printf "%.0f", a - b }')"
# The alert value is increase(...[5m]) at the evaluation that fired, not the cumulative counter:
# older failures have already left the range, so the value tracks the threshold (3), not the total.
awk -v v="$FIRED_VALUE" -v t="$BATCH" 'BEGIN { exit !(v + 0 >= t - 0.01) }' ||
  die "alert value $FIRED_VALUE is below the rule threshold $BATCH"
pass "fired at t+${FIRED_ELAPSED}s with value $FIRED_VALUE (threshold $BATCH, $EXPECTED failures generated)"

step "8. the same alert becomes visible in Alertmanager"
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

step "9. wait for the alert to return to inactive"
info "the last failure leaves the 5m range 5m after batch 2; the alert resolves after that"
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
printf 'counter: baseline %s -> %s (%s failures in %s batches, %ss apart)\n' \
  "$BASELINE" "$AFTER_BATCH" "$EXPECTED" "$BATCHES" "$GAP"
printf 'rule: health=ok, lastEvaluation advanced %s -> %s\n' "${RULE_A##* }" "${RULE_B##* }"
printf 'fired at t+%ss with value %s (activeAt %s)\n' \
  "$FIRED_ELAPSED" "$FIRED_VALUE" "$(printf '%s' "$FIRED" | awk '{print $3}')"
