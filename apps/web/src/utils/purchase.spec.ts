import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { describePurchaseError, formatPrice, readTicket } from './purchase'

describe('readTicket', () => {
  it('reads a ticket from the query string', () => {
    expect(readTicket('?ticket=abc123&from=mp')).toBe('abc123')
  })

  it('treats a missing or blank ticket as absent', () => {
    expect(readTicket('')).toBeNull()
    expect(readTicket('?ticket=%20')).toBeNull()
  })
})

describe('describePurchaseError', () => {
  it('tells the buyer the course is already owned', () => {
    expect(describePurchaseError(new ApiError(61002, 'course already owned'))).toBe(
      '你已拥有该课程',
    )
  })

  it('maps an offline course and a missing payment channel', () => {
    expect(describePurchaseError(new ApiError(61001, 'offline'))).toBe('课程已下架，暂时无法购买')
    expect(describePurchaseError(new ApiError(50205, 'not configured'))).toBe(
      '支付通道尚未配置，请联系客服',
    )
  })

  it('asks the buyer to restart the flow when credentials expired', () => {
    expect(describePurchaseError(new ApiError(40102, 'expired'))).toBe(
      '登录凭证已失效，请回到小程序重新获取购买链接',
    )
  })

  it('keeps a transport failure readable and never hides it', () => {
    expect(describePurchaseError(new ApiError(0, '网络连接失败，请检查网络后重试'))).toBe(
      '网络连接失败，请检查网络后重试',
    )
    expect(describePurchaseError(new Error('boom'))).toBe('boom')
  })
})

describe('formatPrice', () => {
  it('formats yuan with two decimals', () => {
    expect(formatPrice(99)).toBe('99.00')
    expect(formatPrice(undefined)).toBe('--')
  })
})
