#!/usr/bin/env bash
# MySQL smoke check: container state, host port reachability, and the six business databases.
set -uo pipefail

# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

name="mysql"
port="${LP_MYSQL_PORT:-3306}"
user="${LP_FLYWAY_USER:-lp_app}"
password="${LP_FLYWAY_PASSWORD:-lp_app_dev}"

require_container lp-mysql || exit 1

if ! port_open 127.0.0.1 "$port"; then
  say_fail "$name" "127.0.0.1:${port} is not reachable; check LP_MYSQL_PORT in infra/.env and whether port ${port} is taken by another process"
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  say_fail "$name" "mysql client not found in PATH; cannot verify the six databases"
  exit 1
fi

output="$(MYSQL_PWD="$password" mysql --connect-timeout=5 -h127.0.0.1 -P"$port" -u"$user" -N -B -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('lp_ums','lp_cms','lp_oms','lp_lms','lp_ugc','lp_ai');" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
  say_fail "$name" "cannot connect as ${user} to 127.0.0.1:${port}: ${output}"
  exit 1
fi

if [ "$output" -ne 6 ]; then
  say_fail "$name" "expected 6 business databases, found ${output}; run infra/mysql/init/00-create-databases.sql"
  exit 1
fi

say_pass "$name" "127.0.0.1:${port} reachable, login ok, 6 business databases present"
