#!/usr/bin/env bash
# RocketMQ smoke check: NameServer and broker ports plus a cluster listing.
set -uo pipefail

# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

name="rocketmq"
namesrv_port="${LP_ROCKETMQ_NAMESRV_PORT:-9876}"
broker_port=10911

require_container lp-rocketmq-namesrv || exit 1
require_container lp-rocketmq-broker || exit 1

if ! port_open 127.0.0.1 "$namesrv_port"; then
  say_fail "$name" "NameServer 127.0.0.1:${namesrv_port} is not reachable; check LP_ROCKETMQ_NAMESRV_PORT"
  exit 1
fi

if ! port_open 127.0.0.1 "$broker_port"; then
  say_fail "$name" "broker 127.0.0.1:${broker_port} is not reachable; inspect docker logs lp-rocketmq-broker"
  exit 1
fi

# mqadmin lives inside the image only; run it there and rely on its exit code.
output="$(docker exec lp-rocketmq-namesrv sh -c './mqadmin clusterList -n localhost:9876' 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
  say_fail "$name" "mqadmin clusterList failed: $(printf '%s' "$output" | tail -n 3)"
  exit 1
fi

say_pass "$name" "NameServer ${namesrv_port} and broker ${broker_port} reachable, cluster listing ok"
