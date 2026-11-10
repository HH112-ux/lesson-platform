#!/usr/bin/env bash
# Commit message check following the team git convention:
# type(scope): subject, English imperative, lowercase first word, <= 50 characters,
# ASCII only, body lines <= 72 characters.
# Usage: check-commit-msg.sh <message-file> [commit-sha]
# The optional sha lets the checker skip merge commits (more than one parent); CI passes it
# because it validates every commit of a pull request.
set -uo pipefail

MAX_LENGTH=50
MAX_BODY_LINE=72

msg_file="${1:-}"
[ -z "$msg_file" ] && { echo "FAIL missing commit message file argument"; exit 1; }
[ -f "$msg_file" ] || { echo "FAIL commit message file not found: $msg_file"; exit 1; }

# POSIX character classes keep BSD sed (macOS) and GNU sed (CI) identical; \s is not portable.
subject="$(head -n 1 "$msg_file" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
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
scopes='gateway|auth|course|trade|learn|ugc|admin|ai|web|admin-web|miniapp|db|ci|docs|deps'
if ! printf '%s' "$subject" | grep -qE "^($types)(\(($scopes)\))?: [a-z0-9]"; then
  echo "FAIL subject must look like 'type(scope): subject', type in: ${types//|/ }"
  echo "     scope must be one of: ${scopes//|/ }, or omitted"
  echo "     subject starts with a lowercase word"
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
  *.) echo "FAIL subject must not end with a period: $subject"; exit 1 ;;
esac

# Commits are English so changelog tooling and git log --grep keep working.
if LC_ALL=C grep -q '[^ -~]' "$msg_file"; then
  echo "FAIL commit message must be ASCII English; put Chinese only in the PR description"
  exit 1
fi

# Body and footer lines stay readable in an 80 column terminal.
line_number=0
while IFS= read -r line || [ -n "$line" ]; do
  line_number=$((line_number + 1))
  [ "$line_number" -eq 1 ] && continue
  line_len="$(printf '%s' "$line" | wc -m | tr -d ' ')"
  if [ "$line_len" -gt "$MAX_BODY_LINE" ]; then
    echo "FAIL commit message line ${line_number} is ${line_len} characters, limit is ${MAX_BODY_LINE}"
    echo "     actual: $line"
    exit 1
  fi
done < "$msg_file"

exit 0
