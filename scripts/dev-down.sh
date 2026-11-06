#!/usr/bin/env bash
#
# Stops the local stack started by scripts/dev-up.sh and releases every published port.
# Named volumes are kept so the database and uploaded objects survive a restart; pass
# --volumes to wipe them as well.
#
# Usage: bash scripts/dev-down.sh [--volumes]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ ! -f infra/.env ]; then
  cp infra/.env.example infra/.env
fi

docker compose -f infra/docker-compose.yml --env-file infra/.env down "$@"

printf 'stack stopped; published ports released\n'
