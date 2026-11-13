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
    const mapped = MESSAGES[error.code]
    if (mapped) {
      return mapped
    }
    if (error.code >= 50000 && error.code < 60000) {
      return '服务暂时不可用，请稍后重试'
    }
    return error.message || fallback
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

/**
 * Messages for the sign-in screen, where {@code 40101} means bad credentials rather than a lost
 * session and throttling must stay distinct from both.
 */
const LOGIN_MESSAGES: Record<number, string> = {
  40101: '账号或密码不正确',
  429: '登录尝试过于频繁，请稍后重试',
  42901: '登录尝试过于频繁，请稍后重试',
  67001: '账号已注销，无法登录',
}

/**
 * Turns a login failure into text safe for the sign-in screen.
 *
 * <p>The shared map reports {@code 40101} as an expired session because that is what the code means
 * on authenticated calls. The login endpoint is anonymous and uses the same code for a wrong
 * account or password, so it needs its own wording. Server side failures keep their own message
 * and are never reported as credentials errors.</p>
 *
 * @param error value thrown by the login request
 * @param fallback text used when the error carries no message
 * @returns display text for the sign-in notice
 * @author Caspian
 * @since 0.1.0
 */
export function describeLoginError(error: unknown, fallback = '登录失败，请稍后重试'): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error && error.message ? error.message : fallback
  }
  const mapped = LOGIN_MESSAGES[error.code]
  if (mapped) {
    return mapped
  }
  if (error.code >= 50000 && error.code < 60000) {
    return '服务暂时不可用，请稍后重试'
  }
  return describeError(error, fallback)
}

/**
 * Picks the notice of the sign-in screen.
 *
 * <p>Only the credential check itself may report wrong credentials. Once the credentials were
 * accepted, a later failure is a session or service problem and keeps the shared wording, so a
 * backend outage is never presented as a bad password.</p>
 *
 * @param error value thrown while signing in
 * @param credentialsAccepted whether the login endpoint already accepted the credentials
 * @returns display text for the sign-in notice
 * @author Caspian
 * @since 0.1.0
 */
export function describeSignInError(error: unknown, credentialsAccepted: boolean): string {
  return credentialsAccepted
    ? describeError(error, '登录失败，请稍后重试')
    : describeLoginError(error)
}
