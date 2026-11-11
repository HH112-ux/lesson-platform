#!/usr/bin/env bash
# Runs the k6 load scenarios against the local stack.
#
# Usage: bash scripts/run-load.sh
# Inputs: LP_LOAD_* values are resolved by scripts/load-preflight.sh.
# Output: logs/load/summary.json plus k6 console output. logs/ is gitignored.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K6_IMAGE="${LP_LOAD_K6_IMAGE:-grafana/k6:0.54.1}"
OUT_DIR="${ROOT}/logs/load"
BASE_URL="${LP_LOAD_BASE_URL:-http://localhost:8080}"
LIMIT="${LP_LOAD_GATEWAY_LIMIT_PER_MINUTE:-100000}"

if ! command -v k6 >/dev/null 2>&1 && ! command -v docker >/dev/null 2>&1; then
  printf 'ERROR 既没有本机 k6 也没有 docker；装一个再跑（brew install k6）。\n' >&2
  exit 1
fi

# The gateway rejects /api/v1/** above lp.rate-limit.gateway.limit-per-minute per
# key (user id, or the client ip while anonymous), and every scenario here shares
# one key. Measuring against the dev default of 300/min would report the limiter
# instead of the services, so raise it for this run and say so.
if command -v docker >/dev/null 2>&1 \
  && docker compose version >/dev/null 2>&1 \
  && docker inspect lp-gateway >/dev/null 2>&1; then
  current="$(docker inspect lp-gateway \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^LP_GATEWAY_RATE_LIMIT_PER_MINUTE=//p')"
  if [ "${current:-300}" != "${LIMIT}" ]; then
    printf '提升网关限流 %s → %s 次/分（仅本次压测；bash scripts/dev-up.sh 恢复默认）\n' \
      "${current:-300}" "${LIMIT}" >&2
    LP_GATEWAY_RATE_LIMIT_PER_MINUTE="${LIMIT}" \
      docker compose -f "${ROOT}/infra/docker-compose.yml" \
        --env-file "${ROOT}/infra/.env" up -d --no-deps gateway >/dev/null
    for _ in $(seq 1 60); do
      if curl -fsS -o /dev/null "${BASE_URL}/actuator/health"; then
        break
      fi
      sleep 1
    done
  fi
fi

env_file="$(mktemp)"
trap 'rm -f "${env_file}"' EXIT

bash "${ROOT}/scripts/load-preflight.sh" > "${env_file}"
# shellcheck disable=SC1090
. "${env_file}"

mkdir -p "${OUT_DIR}"

if command -v k6 >/dev/null 2>&1; then
  printf '压测目标：%s（本机 k6）\n' "${LP_LOAD_BASE_URL}"
  (
    cd "${ROOT}/scripts/load"
    env \
      "LP_LOAD_BASE_URL=${LP_LOAD_BASE_URL}" \
      "LP_LOAD_COURSE_IDS=${LP_LOAD_COURSE_IDS}" \
      "LP_LOAD_PAID_COURSE_IDS=${LP_LOAD_PAID_COURSE_IDS}" \
      "LP_LOAD_OWNED_LESSON_ID=${LP_LOAD_OWNED_LESSON_ID}" \
      k6 run main.js --summary-export "${OUT_DIR}/summary.json"
  )
else
  container_base="${LP_LOAD_BASE_URL}"
  container_base="${container_base//localhost/host.docker.internal}"
  container_base="${container_base//127.0.0.1/host.docker.internal}"
  printf '压测目标：%s（容器内 %s）\n' "${LP_LOAD_BASE_URL}" "${container_base}"
  printf '镜像：%s\n' "${K6_IMAGE}"
  docker run --rm -i \
    --add-host=host.docker.internal:host-gateway \
    -v "${ROOT}/scripts/load:/scripts:ro" \
    -v "${OUT_DIR}:/out" \
    -w /scripts \
    -e "LP_LOAD_BASE_URL=${container_base}" \
    -e "LP_LOAD_COURSE_IDS=${LP_LOAD_COURSE_IDS}" \
    -e "LP_LOAD_PAID_COURSE_IDS=${LP_LOAD_PAID_COURSE_IDS}" \
    -e "LP_LOAD_OWNED_LESSON_ID=${LP_LOAD_OWNED_LESSON_ID}" \
    "${K6_IMAGE}" run main.js --summary-export /out/summary.json
fi

printf 'summary: %s\n' "${OUT_DIR}/summary.json"
