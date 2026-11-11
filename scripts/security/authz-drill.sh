#!/usr/bin/env bash
# Identity and authorization drill for the L1-10 gate.
#
# Threat surface: forged gateway identity headers, the four V7
# admin:reconcile:* codes with real JWTs over HTTP, cross-user teacher and
# buyer access, upload session ownership and private object storage.
# Every assertion prints PASS/FAIL with its evidence; one FAIL exits non-zero.
#
# Usage: bash scripts/security/authz-drill.sh
# Side effects: one reconcile row may be marked handled.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=lib.sh
. "${ROOT}/scripts/security/lib.sh"

TRADE_DIRECT="${LP_SEC_TRADE_DIRECT_URL:-http://127.0.0.1:8103}"
MINIO="${LP_SEC_MINIO_URL:-http://127.0.0.1:9000}"

if ! curl -fsS -o /dev/null "${SEC_BASE}/actuator/health"; then
  printf 'ERROR 网关不可用：%s/actuator/health，先执行 bash scripts/dev-up.sh。\n' \
    "${SEC_BASE}" >&2
  exit 1
fi

ADMIN="$(login demo_admin)"
OPS="$(login demo_ops)"
BUYER="$(login demo_buyer)"
GUEST="$(login demo_guest)"
TEACHER_LIN="$(login demo_teacher_lin)"
TEACHER_SU="$(login demo_teacher_su)"
require_login demo_admin "${ADMIN}"
require_login demo_ops "${OPS}"
require_login demo_buyer "${BUYER}"
require_login demo_guest "${GUEST}"
require_login demo_teacher_lin "${TEACHER_LIN}"
require_login demo_teacher_su "${TEACHER_SU}"

RECHECK_ID="$(mysql_query "SELECT id FROM lp_oms.payment_reconcile ORDER BY id LIMIT 1")"
PENDING_ID="$(mysql_query \
  "SELECT id FROM lp_oms.payment_reconcile WHERE status = 2 ORDER BY id LIMIT 1")"
BILL_DAY="$(mysql_query "SELECT bill_date FROM lp_oms.payment_reconcile WHERE id = ${RECHECK_ID}")"
LIN="$(mysql_query "
  SELECT c.id, l.id FROM lp_cms.course c
    JOIN lp_cms.teacher_profile t ON t.id = c.owner_teacher_id
    JOIN lp_cms.course_lesson l ON l.course_id = c.id
  WHERE t.user_id = 21 AND l.video_url <> '' ORDER BY l.id LIMIT 1")"
SU="$(mysql_query "
  SELECT c.id, l.id FROM lp_cms.course c
    JOIN lp_cms.teacher_profile t ON t.id = c.owner_teacher_id
    JOIN lp_cms.course_lesson l ON l.course_id = c.id
  WHERE t.user_id = 22 AND l.video_url <> '' ORDER BY l.id LIMIT 1")"
BUYER_ORDER="$(mysql_query "
  SELECT o.id FROM lp_oms.trade_order o JOIN lp_ums.sys_user u ON u.id = o.user_id
  WHERE u.username = 'demo_buyer' ORDER BY o.id DESC LIMIT 1")"
read -r LIN_COURSE LIN_LESSON <<< "${LIN}"
read -r SU_COURSE SU_LESSON <<< "${SU}"
LIN_CHAPTER="$(mysql_query "SELECT chapter_id FROM lp_cms.course_lesson WHERE id = ${LIN_LESSON}")"
SU_CHAPTER="$(mysql_query "SELECT chapter_id FROM lp_cms.course_lesson WHERE id = ${SU_LESSON}")"
OBJECT_KEY="$(mysql_query "SELECT video_url FROM lp_cms.course_lesson WHERE id = ${LIN_LESSON}")"

require_value 'reconcile' "${RECHECK_ID}" 'lp_oms.payment_reconcile 为空'
require_value 'bill-day' "${BILL_DAY}" '对账行没有账单日'
require_value 'lin-course-lesson' "${LIN}" '教师 21 名下没有带视频的课时'
require_value 'su-course-lesson' "${SU}" '教师 22 名下没有带视频的课时'
require_value 'buyer-order' "${BUYER_ORDER}" 'demo_buyer 没有订单'
require_value 'object-key' "${OBJECT_KEY}" '课时没有 video_url'

printf '环境：%s，对账行=%s，账单日=%s\n' "${SEC_BASE}" "${RECHECK_ID}" "${BILL_DAY}"

echo '== 1. 伪造网关注入身份头'
raw_url "${SEC_BASE}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view'
expect_code 'gateway anonymous with forged identity headers' 40101

raw_url "${SEC_BASE}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H "Authorization: Bearer ${BUYER}" -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view'
expect_code 'gateway student token with forged admin headers' 40301

raw_url "${TRADE_DIRECT}/api/v1/reconcile/daily?billDate=${BILL_DAY}" \
  -H 'X-User-Id: 6' -H 'X-User-Roles: ADMIN' \
  -H 'X-User-Permissions: admin:reconcile:view,admin:reconcile:recheck,admin:reconcile:handle,admin:reconcile:close'
expect_not_code 'direct service call with forged identity headers' 0

raw_url "${TRADE_DIRECT}/internal/v1/orders/stats" -H 'X-Internal-Token: wrong'
expect_code 'internal endpoint without the shared token' 40101

echo '== 2. V7 admin:reconcile:* 权限码（真实 JWT 走 HTTP）'
request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${ADMIN}" -
expect_code 'admin:reconcile:view via admin JWT' 0

request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${OPS}" -
expect_code 'admin:reconcile:view via ops JWT (V7 grants OPS)' 0

request POST "/api/v1/reconcile/${RECHECK_ID}/recheck" "${ADMIN}" -
expect_not_code 'admin:reconcile:recheck via admin JWT' 40301

if [ -n "${PENDING_ID}" ]; then
  request POST "/api/v1/reconcile/${PENDING_ID}/handle" "${ADMIN}" -
else
  request POST "/api/v1/reconcile/${RECHECK_ID}/handle" "${ADMIN}" -
fi
expect_not_code 'admin:reconcile:handle via admin JWT' 40301

request POST '/api/v1/reconcile/settlements' "${ADMIN}" \
  "{\"channel\":1,\"billDate\":\"${BILL_DAY}\"}"
expect_not_code 'admin:reconcile:close via admin JWT' 40301

request POST '/api/v1/reconcile/settlements/999999/void' "${ADMIN}" \
  '{"reason":"security drill"}'
expect_code 'void gate reached with admin role and close code' 40401

request POST '/api/v1/reconcile/settlements' "${OPS}" \
  "{\"channel\":1,\"billDate\":\"${BILL_DAY}\"}"
expect_code 'close rejected for ops JWT (V7 grants close to ADMIN only)' 40301

request POST '/api/v1/reconcile/settlements/999999/void' "${OPS}" \
  '{"reason":"security drill"}'
expect_code 'void rejected for ops JWT' 40301

request GET "/api/v1/reconcile/daily?billDate=${BILL_DAY}" "${BUYER}" -
expect_code 'reconcile rejected for student JWT' 40301

echo '== 3. 跨用户越权'
request GET "/api/v1/teacher/courses/${LIN_COURSE}" "${TEACHER_LIN}" -
expect_code 'teacher reads own course' 0

request GET "/api/v1/teacher/courses/${SU_COURSE}" "${TEACHER_LIN}" -
expect_code 'teacher reads another teacher course' 40301

request GET \
  "/api/v1/teacher/courses/${LIN_COURSE}/chapters/${LIN_CHAPTER}/lessons/${LIN_LESSON}/play-url" \
  "${TEACHER_LIN}" -
expect_code 'teacher signs a play url for an owned lesson' 0

request GET \
  "/api/v1/teacher/courses/${SU_COURSE}/chapters/${SU_CHAPTER}/lessons/${SU_LESSON}/play-url" \
  "${TEACHER_LIN}" -
expect_code 'teacher signs a play url for another teacher lesson' 40301

request GET "/api/v1/teacher/courses" "${BUYER}" -
expect_code 'student lists teacher courses' 40301

request GET "/api/v1/orders/${BUYER_ORDER}" "${GUEST}" -
expect_code 'student reads another student order' 40301

request GET "/api/v1/orders/${BUYER_ORDER}" "${BUYER}" -
expect_code 'student reads own order' 0

echo '== 4. 上传会话与对象存储越权'
request POST \
  "/api/v1/teacher/courses/${SU_COURSE}/chapters/${SU_CHAPTER}/lessons/${SU_LESSON}/upload-sessions" \
  "${TEACHER_LIN}" '{"expectedSizeBytes":1048576}'
expect_code 'teacher opens an upload session on another teacher lesson' 40301

request POST \
  "/api/v1/teacher/courses/${LIN_COURSE}/chapters/${LIN_CHAPTER}/lessons/${LIN_LESSON}/upload-sessions" \
  "${TEACHER_LIN}" '{"expectedSizeBytes":1048576}'
expect_not_code 'teacher opens an upload session on an owned lesson' 40301

raw_url "${MINIO}/lp-media/${OBJECT_KEY}"
expect_http 'private bucket object without a signature' 403

summary
