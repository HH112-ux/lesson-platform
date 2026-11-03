#!/usr/bin/env bash
# Enable the repository-local git hooks and print the verification result.
# Usage: bash scripts/install-hooks.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HOOKS_DIR=".githooks"
REQUIRED_HOOKS="pre-commit commit-msg"

if [ ! -d "$HOOKS_DIR" ]; then
  printf 'FAIL hooks directory not found: %s\n' "$HOOKS_DIR"
  exit 1
fi

missing=0
for hook in $REQUIRED_HOOKS; do
  path="$HOOKS_DIR/$hook"
  if [ ! -f "$path" ]; then
    printf 'FAIL hook missing: %s\n' "$path"
    missing=1
  elif [ ! -x "$path" ]; then
    printf 'FAIL hook is not executable: %s\n' "$path"
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  exit 1
fi

git config core.hooksPath "$HOOKS_DIR"

configured="$(git config core.hooksPath || true)"
if [ "$configured" != "$HOOKS_DIR" ]; then
  printf 'FAIL core.hooksPath is "%s", expected "%s"\n' "$configured" "$HOOKS_DIR"
  exit 1
fi

printf 'OK core.hooksPath = %s\n' "$configured"
for hook in $REQUIRED_HOOKS; do
  printf 'OK hook executable: %s\n' "$HOOKS_DIR/$hook"
done
printf 'Hooks enabled. Each commit now runs scripts/check-docs.sh and scripts/check-commit-msg.sh.\n'
