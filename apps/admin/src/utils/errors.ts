/**
 * Error codes of the unified envelope mapped to operator friendly text.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { ApiError } from '../api/client'

const MESSAGES: Record<number, string> = {
  40001: '请求参数有误，请检查后重试',
  40101: '登录已失效，请重新登录',
  40102: '登录已过期，请重新登录',
  40301: '当前账号没有该操作权限',
  40401: '数据不存在或已被删除',
  40901: '数据状态已变化，请刷新后重试',
  50205: '支付渠道暂时不可用',
  50206: '第三方服务暂时不可用',
  61001: '课程尚未上架',
  61003: '视频未通过校验：需 MP4/H.264/AAC、分辨率不超过 1080p、时长 5 秒至 4 小时',
  61005: '课程状态不允许审核',
  61007: '上传会话已失效，请重新创建会话后再上传',
  61008: '上传状态已变化，请刷新后重新创建会话',
}

/**
 * Turns an unknown error into text safe for the operator.
 *
 * @param error value thrown by a request
 * @param fallback text used when the error carries no message
 * @returns display text
 * @author Caspian
 * @since 0.2.0
 */
export function describeError(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.code] ?? (error.message || fallback)
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
