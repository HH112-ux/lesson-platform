#!/usr/bin/env bash
# 文档规范校验：文件名、相对链接、HTML 标签、旧命名残留
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
fail_msg() { printf 'FAIL %s\n' "$1"; fail=1; }

# 1. 文件名必须为英文短语 kebab-case.md（无数字前缀，正则本身排除大写与中文）
while IFS= read -r f; do
  base="$(basename "$f")"
  if ! printf '%s' "$base" | grep -qE '^[a-z0-9]+(-[a-z0-9]+)*\.md$'; then
    fail_msg "文件名不符合英文 kebab-case 命名（无数字前缀）：$f"
  fi
done < <(find docs -type f -name '*.md' | sort)

# 2. Markdown 相对链接必须可解析
while IFS= read -r f; do
  dir="$(dirname "$f")"
  while IFS= read -r raw; do
    target="$(printf '%s' "$raw" | sed -E 's/^\]\(//; s/\)$//')"
    case "$target" in
      http://*|https://*|mailto:*|\#*) continue ;;
    esac
    path="${target%%#*}"
    [ -z "$path" ] && continue
    if [ ! -e "$dir/$path" ]; then
      fail_msg "链接目标不存在：$f -> $target"
    fi
  done < <(grep -oE '\]\([^)]+\)' "$f" | sort -u)
done < <(find . -maxdepth 3 -type f -name '*.md' -not -path './.git/*' | sort)

# 3. 禁止 HTML 标签
while IFS= read -r f; do
  hits="$(grep -nE '<(div|span|table|script|iframe|style|p|img|ul|ol|li|h[1-6])([ >/])' "$f" || true)"
  if [ -n "$hits" ]; then
    fail_msg "发现 HTML 标签：$f"
    printf '%s\n' "$hits"
  fi
done < <(find . -maxdepth 3 -type f -name '*.md' -not -path './.git/*' | sort)

# 4. 旧命名残留
check_residual() {
  local pattern="$1" desc="$2"
  local hits
  hits="$(grep -rnE "$pattern" AGENTS.md docs | grep -vE '禁止|反例|不得' || true)"
  if [ -n "$hits" ]; then
    fail_msg "旧命名残留（${desc}）"
    printf '%s\n' "$hits"
  fi
}
check_residual 'agent-git-rules' '旧规则文件名'
check_residual 'X-Auth-(Ts|Sign)( |`|/|$)' '请求头缩写'
check_residual '\*\*orders ' '旧表名 orders'
check_residual '\borders\.(pay_amount|status|channel)' '旧表名 orders'
check_residual '\border_item\b' '旧表名 order_item'
check_residual '课程平台工程规划与上线方案\.md' '旧文档文件名'
check_residual 'create_time|gmt_create' '时间字段旧命名'
check_residual '其它' '错字'

if [ "$fail" -ne 0 ]; then
  printf '\n文档校验失败，请按上述提示修复。\n'
  exit 1
fi
printf 'OK 文档校验通过\n'
