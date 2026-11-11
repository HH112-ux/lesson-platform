#!/usr/bin/env bash
# Resolves the ids the load scenarios need and checks the local baseline.
#
# The public API exposes no course list, so ids come from the containerised
# MySQL. Nothing is seeded here: the run relies on db/seed/demo-seed.sql, and
# fails loudly when the D12 baseline (20 published courses / 200 lessons) is
# missing instead of measuring a half-empty database.
#
# Output: KEY=VALUE lines on stdout, sourceable by scripts/run-load.sh.
set -euo pipefail

BASE_URL="${LP_LOAD_BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${LP_LOAD_MYSQL_CONTAINER:-lp-mysql}"
BUYER="${LP_LOAD_ACCOUNT:-demo_buyer}"

if ! curl -fsS -o /dev/null "${BASE_URL}/actuator/health"; then
  printf 'ERROR 网关不可用：%s/actuator/health。先执行 bash scripts/dev-up.sh。\n' "${BASE_URL}" >&2
  exit 1
fi

if ! docker inspect "${MYSQL_CONTAINER}" >/dev/null 2>&1; then
  printf 'ERROR 找不到容器 %s，无法读取压测数据基线。\n' "${MYSQL_CONTAINER}" >&2
  exit 1
fi

# Read the root password from the container env so it never lands in the shell
# history or in this script.
mysql_query() {
  local password
  password="$(docker inspect "${MYSQL_CONTAINER}" \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p')"
  if [ -z "${password}" ]; then
    printf 'ERROR 容器 %s 没有 MYSQL_ROOT_PASSWORD。\n' "${MYSQL_CONTAINER}" >&2
    exit 1
  fi
  docker exec -e MYSQL_PWD="${password}" "${MYSQL_CONTAINER}" mysql -uroot -N -B -e "$1"
}

published_courses="$(mysql_query \
  "SELECT COUNT(*) FROM lp_cms.course WHERE status = 2")"
lessons="$(mysql_query "SELECT COUNT(*) FROM lp_cms.course_lesson")"

if [ "${published_courses}" -lt 20 ] || [ "${lessons}" -lt 200 ]; then
  printf 'ERROR 数据基线不足：已上架课程 %s/20，课时 %s/200。先执行 bash scripts/seed/seed-demo.sh。\n' \
    "${published_courses}" "${lessons}" >&2
  exit 1
fi

course_ids="$(mysql_query \
  "SELECT id FROM lp_cms.course WHERE status = 2 ORDER BY id" | paste -sd, -)"

paid_course_ids="$(mysql_query "
  SELECT c.id
  FROM lp_cms.course c
  WHERE c.status = 2 AND c.price > 0
    AND NOT EXISTS (SELECT 1
                    FROM lp_lms.user_entitlement e
                             JOIN lp_ums.sys_user u ON u.id = e.user_id
                    WHERE e.course_id = c.id AND u.username = '${BUYER}')
  ORDER BY c.id" | paste -sd, -)"

owned_lesson_id="$(mysql_query "
  SELECT l.id
  FROM lp_cms.course_lesson l
           JOIN lp_cms.course_chapter ch ON ch.id = l.chapter_id
           JOIN lp_lms.user_entitlement e ON e.course_id = ch.course_id
           JOIN lp_ums.sys_user u ON u.id = e.user_id
  WHERE u.username = '${BUYER}' AND l.status = 1
  ORDER BY l.id
  LIMIT 1")"

if [ -z "${paid_course_ids}" ]; then
  printf 'ERROR %s 已拥有全部付费课程，下单场景没有可用课程。\n' "${BUYER}" >&2
  exit 1
fi
if [ -z "${owned_lesson_id}" ]; then
  printf 'ERROR %s 没有可用课时，进度场景无法执行。\n' "${BUYER}" >&2
  exit 1
fi

printf 'LP_LOAD_BASE_URL=%s\n' "${BASE_URL}"
printf 'LP_LOAD_COURSE_IDS=%s\n' "${course_ids}"
printf 'LP_LOAD_PAID_COURSE_IDS=%s\n' "${paid_course_ids}"
printf 'LP_LOAD_OWNED_LESSON_ID=%s\n' "${owned_lesson_id}"
printf '基线：已上架课程 %s，课时 %s，买方可下单付费课 %s 门。\n' \
  "${published_courses}" "${lessons}" \
  "$(printf '%s' "${paid_course_ids}" | tr ',' '\n' | wc -l | tr -d ' ')" >&2
