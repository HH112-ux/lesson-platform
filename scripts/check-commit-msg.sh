#!/usr/bin/env bash
# Commit message check: type(scope): subject, English imperative, lowercase, <= 60 characters.
# Usage: check-commit-msg.sh <message-file> [commit-sha]
# The optional sha lets the checker skip merge commits (more than one parent); CI passes it
# because it validates every commit of a pull request.
set -uo pipefail

MAX_LENGTH=60

msg_file="${1:-}"
[ -z "$msg_file" ] && { echo "FAIL missing commit message file argument"; exit 1; }
[ -f "$msg_file" ] || { echo "FAIL commit message file not found: $msg_file"; exit 1; }

subject="$(head -n 1 "$msg_file" | sed -E 's/^\s+|\s+$//g')"
[ -z "$subject" ] && { echo "FAIL empty first line in commit message"; exit 1; }

# Merge commits are produced by git itself, never by a person or an agent.
case "$subject" in
  Merge\ *) exit 0 ;;
esac
sha="${2:-}"
if [ -n "$sha" ] && git rev-parse --git-dir >/dev/null 2>&1; then
  parent_count="$(git rev-list --parents -n 1 "$sha" 2>/dev/null | wc -w | tr -d ' ')"
  if [ "${parent_count:-0}" -gt 2 ]; then
    exit 0
  fi
fi

# git revert keeps its own wording; the revert type is already whitelisted.
case "$subject" in
  Revert\ *|revert:*) exit 0 ;;
esac

# GitHub appends " (#<pr>)" to the PR title when squashing. Strip it before validating,
# otherwise a compliant title fails only after the merge.
subject="$(printf '%s' "$subject" | sed -E 's/ \(#([0-9]+)\)$//')"
[ -z "$subject" ] && { echo "FAIL empty subject after stripping the squash suffix"; exit 1; }

types='feat|fix|docs|style|refactor|perf|test|chore|revert'
if ! printf '%s' "$subject" | grep -qE "^($types)(\([a-z0-9_-]+\))?: [^ ]"; then
  echo "FAIL subject must look like 'type(scope): subject', type in: ${types//|/ }"
  echo "     actual: $subject"
  exit 1
fi

len="$(printf '%s' "$subject" | wc -m | tr -d ' ')"
if [ "$len" -gt "$MAX_LENGTH" ]; then
  echo "FAIL subject is ${len} characters, limit is ${MAX_LENGTH}"
  echo "     actual: $subject"
  exit 1
fi

case "$subject" in
  *。) echo "FAIL subject must not end with a period: $subject"; exit 1 ;;
esac

exit 0
