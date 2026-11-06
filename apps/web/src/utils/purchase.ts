/**
 * Pure helpers of the browser purchase flow.
 *
 * <p>They stay free of DOM and fetch access so the view stays thin and the rules are unit tested.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
import { ApiError } from '../api/client'

/** Response of {@code POST /api/v1/orders/purchase-ticket/redeem}. */
export interface RedeemResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  courseId: number
}

/**
 * Reads the one-off ticket from a query string.
 *
 * @param search raw {@code window.location.search} value
 * @returns the ticket, or {@code null} when absent or blank
 * @author Caspian
 * @since 0.1.0
 */
export function readTicket(search: string): string | null {
  const ticket = new URLSearchParams(search).get('ticket')
  return ticket && ticket.trim() ? ticket.trim() : null
}

/**
 * Maps a failure to a message a buyer can act on.
 *
 * @param error thrown value
 * @returns Chinese message displayed in the page
 * @author Caspian
 * @since 0.1.0
 */
export function describePurchaseError(error: unknown): string {
  const code = error instanceof ApiError ? error.code : 0
  switch (code) {
    case 61001:
      return '课程已下架，暂时无法购买'
    case 61002:
      return '你已拥有该课程'
    case 62003:
      return '课程已下架，暂时无法购买'
    case 50205:
      return '支付通道尚未配置，请联系客服'
    case 40101:
    case 40102:
      return '登录凭证已失效，请回到小程序重新获取购买链接'
    default:
      return error instanceof Error && error.message
        ? error.message
        : '操作失败，请稍后重试'
  }
}
