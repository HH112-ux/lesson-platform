#!/usr/bin/env bash
# 提交信息校验：type(scope): 中文摘要，首行 ≤ 72 字符
set -uo pipefail

msg_file="${1:-}"
[ -z "$msg_file" ] && { echo "FAIL 缺少提交信息文件参数"; exit 1; }
[ -f "$msg_file" ] || { echo "FAIL 提交信息文件不存在：$msg_file"; exit 1; }

subject="$(head -n 1 "$msg_file" | sed -E 's/^\s+|\s+$//g')"
[ -z "$subject" ] && { echo "FAIL 提交信息首行为空"; exit 1; }

# 允许 git 自动生成的合并/回滚提交
case "$subject" in
  Merge\ *|Revert\ *) exit 0 ;;
esac

types='feat|fix|docs|style|refactor|perf|test|chore|ci|build|revert'
if ! printf '%s' "$subject" | grep -qE "^($types)(\([a-z0-9_-]+\))?: [^ ]"; then
  echo "FAIL 提交信息格式应为 'type(scope): 中文摘要'，type 限：${types//|/ }"
  echo "     实际：$subject"
  exit 1
fi

len="$(printf '%s' "$subject" | wc -m | tr -d ' ')"
if [ "$len" -gt 72 ]; then
  echo "FAIL 提交信息首行 ${len} 字符，超过 72 字符上限"
  echo "     实际：$subject"
  exit 1
fi

case "$subject" in
  *。) echo "FAIL 提交信息首行结尾不要加句号：$subject"; exit 1 ;;
esac

exit 0
