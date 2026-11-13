#!/usr/bin/env bash
#
# Starts the whole local stack: infrastructure, Flyway migrations, demo seed data,
# every backend service, the student web app and the operations console.
#
# Usage: bash scripts/dev-up.sh
#
# Prerequisites: Docker (with compose v2), JDK 17, Node 20 and pnpm on the host.
# The host builds the jars and the two frontends, then compose starts them.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f infra/docker-compose.yml --env-file infra/.env)
PROJECT="lp-dev"
NETWORK="${PROJECT}_default"

log() { printf '\n==> %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker is required"
command -v pnpm >/dev/null 2>&1 || fail "pnpm is required (corepack enable pnpm)"
command -v java >/dev/null 2>&1 || fail "JDK 17 is required"

if [ ! -f infra/.env ]; then
  log "creating infra/.env from infra/.env.example"
  cp infra/.env.example infra/.env
fi
# shellcheck disable=SC1091
set -a
. infra/.env
set +a

if [ ! -f infra/jwt/jwt-private.pem ] || [ ! -f infra/jwt/jwt-public.pem ]; then
  log "generating local RS256 key pair"
  bash scripts/gen-jwt-keys.sh
fi

# Presigned MinIO URLs are consumed by the browser, so learn-service must sign against a
# host reachable address. Fall back to 127.0.0.1 when no LAN address is available.
HOST_IP="$(ipconfig getifaddr en0 2>/dev/null || true)"
if [ -z "$HOST_IP" ]; then
  HOST_IP="$(ipconfig getifaddr en1 2>/dev/null || true)"
fi
export LP_MINIO_PUBLIC_ENDPOINT="http://${HOST_IP:-127.0.0.1}:${LP_MINIO_API_PORT:-9000}"
log "MinIO public endpoint for presigned URLs: ${LP_MINIO_PUBLIC_ENDPOINT}"

log "building backend jars"
./mvnw -B -q -DskipTests package

log "installing frontend dependencies"
pnpm install --frozen-lockfile

log "building student web and operations console"
build_frontend() {
  local filter="$1"
  local output
  if ! output="$(pnpm --filter "${filter}" build 2>&1)"; then
    printf '%s\n' "${output}" >&2
    printf 'ERROR: frontend build failed for %s\n' "${filter}" >&2
    exit 1
  fi
  if printf '%s' "${output}" | grep -q "No projects matched"; then
    printf '%s\n' "${output}" >&2
    printf 'ERROR: no workspace package matched %s\n' "${filter}" >&2
    exit 1
  fi
  printf '%s\n' "${output}"
}
build_frontend lp-web
build_frontend lp-admin

log "starting containers"
# RocketMQ and Nacos stay in the compose file for future use but are not part of the MVP
# runtime; on a laptop the broker can be OOM killed, so only the needed services are
# started and waited on.
SERVICES=(mysql redis minio auth-service course-service trade-service learn-service
          ugc-service admin-service ai-service gateway web admin)
"${COMPOSE[@]}" up -d --build --wait "${SERVICES[@]}"

log "applying Flyway migrations"
for pair in "lp_ums:auth" "lp_cms:course" "lp_oms:trade" "lp_lms:learn" \
            "lp_ugc:ugc" "lp_ai:ai" "lp_ops:ops"; do
  db="${pair%%:*}"
  dir="${pair##*:}"
  printf '  -> %s (%s)\n' "$db" "$dir"
  docker run --rm --network "$NETWORK" \
    -v "$ROOT/db/migration:/flyway/sql:ro" \
    flyway/flyway:10 \
    -url="jdbc:mysql://mysql:3306/${db}?allowPublicKeyRetrieval=true&useSSL=false" \
    -user=lp_app -password=lp_app_dev \
    -locations="filesystem:/flyway/sql/${dir}" \
    -baselineOnMigrate=true \
    migrate >/dev/null
done

log "loading demo seed data"
"${COMPOSE[@]}" exec -T mysql \
  mysql -uroot -p"${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}" < db/seed/demo-seed.sql

log "preparing MinIO bucket and demo media"
# minio/mc is no longer published, but the server image ships the same client, so the
# bucket is created from inside the running container. backup-drill.sh does the same.
# The placeholder must be copied to every key referenced by course_lesson.video_url:
# a single demo/course-1.mp4 object leaves every seeded lesson pointing at a missing key.
MINIO_ROOT_USER="${LP_MINIO_ROOT_USER:-lpminio}"
MINIO_ROOT_PASSWORD="${LP_MINIO_ROOT_PASSWORD:-lp_minio_dev}"
LESSON_KEYS="$("${COMPOSE[@]}" exec -T mysql \
  mysql -uroot -p"${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}" -N -B \
  -e "SELECT DISTINCT video_url FROM lp_cms.course_lesson WHERE video_url IS NOT NULL AND video_url <> ''" \
  2>/dev/null || true)"
if printf '%s\n' "$LESSON_KEYS" | "${COMPOSE[@]}" exec -T minio sh -c '
  mc alias set local http://127.0.0.1:9000 "$1" "$2" >/dev/null || exit 1
  mc mb -p local/lp-media >/dev/null 2>&1 || true
  printf "placeholder video for local playback\n" > /tmp/lesson.mp4 || exit 1
  while IFS= read -r key; do
    [ -n "$key" ] || continue
    mc cp /tmp/lesson.mp4 "local/lp-media/$key" >/dev/null || exit 1
  done
' sh "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; then
  printf '  -> bucket lp-media ready with %s lesson object(s)\n' "$(printf '%s\n' "$LESSON_KEYS" | grep -c . || true)"
else
  printf '  WARN MinIO bucket bootstrap failed; playback URLs will point at missing objects\n' >&2
fi

log "stack is up"
cat <<INFO

  Student web      http://localhost:5173
  Operations UI    http://localhost:5174
  Gateway          http://localhost:8080
  MinIO console    http://localhost:${LP_MINIO_CONSOLE_PORT:-9001}

  Accounts and smoke instructions: README.md

INFO
