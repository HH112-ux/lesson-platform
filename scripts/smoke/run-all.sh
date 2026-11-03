#!/usr/bin/env bash
# Run every infra smoke check and print a final summary. Exits non-zero if any check fails.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKS="mysql redis rocketmq minio nacos"

passed=0
failed=0

for check in $CHECKS; do
  printf '\n--- %s ---\n' "$check"
  if bash "$DIR/${check}.sh"; then
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
  fi
done

printf '\n=== summary: %d passed, %d failed ===\n' "$passed" "$failed"
if [ "$failed" -ne 0 ]; then
  printf 'smoke checks failed\n'
  exit 1
fi
printf 'all smoke checks passed\n'
