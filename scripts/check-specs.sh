#!/usr/bin/env bash
# 规格体系校验：编号、状态、AC 连续性、索引一致性、PR 正文引用
#
# 纯 shell 实现，不依赖 Node；PR 正文来源优先级：
#   1. 环境变量 PR_BODY（直接传入正文）
#   2. 环境变量 PR_BODY_FILE（指向正文文件）
#   3. GITHUB_EVENT_PATH（CI 事件载荷，使用 jq 或 python3 读取 pull_request.body）
# 三者均未提供时跳过引用校验并打印 SKIP，不视为失败。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

DOMAIN_FILES="docs/specs/course.md docs/specs/trade.md docs/specs/learning.md docs/specs/platform.md"
STATUS_FILE="docs/project-status.md"
ID_PATTERN='^SPEC-(COURSE|TRADE|LEARN|PLAT)-[0-9]{3}$'

fail=0
fail_msg() { printf 'FAIL %s\n' "$1"; fail=1; }

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

# ---------- 1. 收集域索引条目 ----------
: > "$tmpdir/index-rows"
for f in $DOMAIN_FILES; do
  if [ ! -f "$f" ]; then
    fail_msg "缺少域索引文件：$f"
    continue
  fi
  awk -F'|' -v file="$f" '
    /^\|[[:space:]]*SPEC-/ {
      id = $2; st = $5;
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", id);
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", st);
      print id "|" st "|" file;
    }' "$f" >> "$tmpdir/index-rows"
done

if [ ! -s "$tmpdir/index-rows" ]; then
  fail_msg "未从域索引中解析到任何规格条目"
fi

cut -d'|' -f1 "$tmpdir/index-rows" | LC_ALL=C sort > "$tmpdir/index-ids-all"

# 2. 编号唯一
dups="$(LC_ALL=C sort "$tmpdir/index-ids-all" | uniq -d)"
if [ -n "$dups" ]; then
  fail_msg "规格编号重复：$(printf '%s' "$dups" | tr '\n' ' ')"
fi

LC_ALL=C sort -u "$tmpdir/index-ids-all" > "$tmpdir/index-ids"

# 3. 编号格式与状态枚举
while IFS='|' read -r id st file; do
  if ! printf '%s' "$id" | grep -qE "$ID_PATTERN"; then
    fail_msg "编号格式不合法（${file}）：$id"
  fi
  case "$st" in
    未开始|进行中|已完成|已废弃) ;;
    *) fail_msg "状态取值不在枚举内（${file}）：$id -> $st" ;;
  esac
done < "$tmpdir/index-rows"

# ---------- 4. 索引 AC 编号连续且不重复 ----------
: > "$tmpdir/index-ac"
while IFS='|' read -r id st file; do
  row="$(awk -F'|' -v id="$id" '$2 ~ ("^[[:space:]]*" id "[[:space:]]*$") {print; exit}' "$file")"
  nums="$(printf '%s\n' "$row" | grep -oE 'AC-[0-9]+' | sed 's/AC-//' | LC_ALL=C sort -n | tr '\n' ' ')"
  expected=1
  for n in $nums; do
    if [ "$n" -ne "$expected" ]; then
      fail_msg "索引 AC 编号不连续（${file} ${id}）：期望 AC-${expected}，实际 AC-${n}"
      break
    fi
    expected=$((expected + 1))
  done
  printf '%s|%s\n' "$id" "$nums" >> "$tmpdir/index-ac"
done < "$tmpdir/index-rows"

# ---------- 5. 详情目录与详情文件 ----------
for d in docs/specs/features/SPEC-*/; do
  [ -d "$d" ] || continue
  id="$(basename "$d")"
  spec="${d}spec.md"

  if ! printf '%s' "$id" | grep -qE "$ID_PATTERN"; then
    fail_msg "详情目录编号格式不合法：$id"
  fi
  if ! grep -qx "$id" "$tmpdir/index-ids"; then
    fail_msg "详情目录编号未在域索引登记：$id"
  fi
  if [ ! -f "$spec" ]; then
    fail_msg "详情目录缺少 spec.md：$id"
    continue
  fi

  declared="$(awk -F'|' '/^\|[[:space:]]*编号[[:space:]]*\|/ {v=$3; gsub(/^[[:space:]]+|[[:space:]]+$/, "", v); print v; exit}' "$spec")"
  if [ "$declared" != "$id" ]; then
    fail_msg "详情文件编号与目录不一致（${spec}）：目录 ${id}，正文 ${declared:-空}"
  fi

  dstatus="$(awk -F'|' '/^\|[[:space:]]*状态[[:space:]]*\|/ {v=$3; gsub(/^[[:space:]]+|[[:space:]]+$/, "", v); print v; exit}' "$spec")"
  case "$dstatus" in
    未开始|进行中|已完成|已废弃) ;;
    *) fail_msg "详情文件状态取值不在枚举内（${spec}）：${dstatus:-空}" ;;
  esac

  section="$(awk '/^## 5\./{f=1; next} /^## 6\./{f=0} f' "$spec")"
  nums="$(printf '%s\n' "$section" | grep -oE '^- AC-[0-9]+[:：]' | grep -oE '[0-9]+' | LC_ALL=C sort -n | tr '\n' ' ')"
  if [ -z "$nums" ]; then
    fail_msg "详情文件缺少 AC 条目（${spec}）"
  fi
  expected=1
  for n in $nums; do
    if [ "$n" -ne "$expected" ]; then
      fail_msg "详情 AC 编号不连续（${spec}）：期望 AC-${expected}，实际 AC-${n}"
      break
    fi
    expected=$((expected + 1))
  done

  index_nums="$(grep "^${id}|" "$tmpdir/index-ac" | cut -d'|' -f2)"
  if [ "$nums" != "$index_nums" ]; then
    fail_msg "详情与索引的 AC 集合不一致（${id}）：索引 [${index_nums}]，详情 [${nums}]"
  fi
done

# ---------- 6. 项目状态索引与域索引双向一致 ----------
if [ ! -f "$STATUS_FILE" ]; then
  fail_msg "缺少进度档案：$STATUS_FILE"
else
  awk -F'|' '/^\|[[:space:]]*SPEC-/ {id=$2; gsub(/^[[:space:]]+|[[:space:]]+$/, "", id); print id}' "$STATUS_FILE" \
    | LC_ALL=C sort -u > "$tmpdir/status-ids"

  missing_in_status="$(comm -23 "$tmpdir/index-ids" "$tmpdir/status-ids")"
  missing_in_specs="$(comm -13 "$tmpdir/index-ids" "$tmpdir/status-ids")"
  if [ -n "$missing_in_status" ]; then
    fail_msg "域索引有但 project-status 缺失：$(printf '%s' "$missing_in_status" | tr '\n' ' ')"
  fi
  if [ -n "$missing_in_specs" ]; then
    fail_msg "project-status 有但域索引缺失：$(printf '%s' "$missing_in_specs" | tr '\n' ' ')"
  fi
fi

# ---------- 7. PR 正文引用反查 ----------
body=""
if [ -n "${PR_BODY:-}" ]; then
  body="$PR_BODY"
elif [ -n "${PR_BODY_FILE:-}" ] && [ -f "$PR_BODY_FILE" ]; then
  body="$(cat "$PR_BODY_FILE")"
elif [ -n "${GITHUB_EVENT_PATH:-}" ] && [ -f "$GITHUB_EVENT_PATH" ]; then
  if command -v jq >/dev/null 2>&1; then
    body="$(jq -r '.pull_request.body // ""' "$GITHUB_EVENT_PATH" 2>/dev/null || true)"
  elif command -v python3 >/dev/null 2>&1; then
    body="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(((d.get("pull_request") or {}).get("body")) or "")' "$GITHUB_EVENT_PATH" 2>/dev/null || true)"
  fi
fi

if [ -n "$body" ]; then
  for ref in $(printf '%s\n' "$body" | grep -oE 'SPEC-(COURSE|TRADE|LEARN|PLAT)-[0-9]{3}' | LC_ALL=C sort -u); do
    if ! grep -qx "$ref" "$tmpdir/index-ids"; then
      fail_msg "PR 正文引用的规格号不存在：$ref"
    fi
  done
else
  printf 'SKIP 未提供 PR 正文，跳过引用校验\n'
fi

if [ "$fail" -ne 0 ]; then
  printf '\n规格校验失败，请按上述提示修复。\n'
  exit 1
fi
printf 'OK 规格校验通过\n'
