/**
 * User facing messages for backend error codes.
 *
 * <p>Views never branch on raw numbers; they call {@link describeError} so every page
 * reports the same wording for the same failure.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { ApiError } from '../api/client'

const MESSAGES: Record<number, string> = {
  40001: '请求参数有误，请检查后重试',
  40101: '请先登录后再操作',
  40102: '登录已过期，请重新登录',
  40301: '当前账号没有操作权限',
  40302: '请先购买该课程',
  40401: '内容不存在或已被删除',
  40901: '用户名已被占用',
  50205: '支付通道尚未配置，请联系客服',
  50206: '文件服务暂不可用，请稍后重试',
  61001: '课程已下架，暂时无法购买',
  61002: '你已拥有该课程',
  62003: '课程不可用，暂时无法购买',
  63001: '学习记录保存失败，请稍后重试',
}

/**
 * Maps a thrown value to a message safe to display.
 *
 * @param error thrown value, usually an {@link ApiError}
 * @param fallback message used when the error carries no useful text
 * @returns Chinese message shown in the page
 * @author Caspian
 * @since 0.2.0
 */
export function describeError(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error && error.message ? error.message : fallback
  }
  const mapped = MESSAGES[error.code]
  if (mapped) {
    return mapped
  }
  if (error.code >= 50000 && error.code < 60000) {
    return error.message || '服务暂时不可用，请稍后重试'
  }
  return error.message || fallback
}

/**
 * Tells whether the failure means the visitor must sign in again.
 *
 * @param error thrown value
 * @returns true for missing or expired credentials
 * @author Caspian
 * @since 0.2.0
 */
export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && (error.code === 40101 || error.code === 40102)
}

/**
 * Tells whether the failure means the resource must be treated as absent.
 *
 * <p>The course detail endpoint returns {@code 40401} for a course that was taken offline and is
 * not owned by the caller, so the page shows a dedicated empty state instead of a generic error.</p>
 *
 * @param error thrown value
 * @returns true for {@code 40401}
 * @author Caspian
 * @since 0.2.0
 */
export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 40401
}
