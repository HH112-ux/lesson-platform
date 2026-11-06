import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { describeError, isUnauthorized } from './errors'

describe('describeError', () => {
  it('maps known backend codes to fixed wording', () => {
    expect(describeError(new ApiError(40901, 'conflict'))).toBe('用户名已被占用')
    expect(describeError(new ApiError(61002, 'owned'))).toBe('你已拥有该课程')
  })

  it('falls back to the backend message for unlisted server errors', () => {
    expect(describeError(new ApiError(50301, '上游超时'))).toBe('上游超时')
  })

  it('handles transport and unknown failures', () => {
    expect(describeError(new ApiError(0, '网络连接失败，请检查网络后重试')))
      .toBe('网络连接失败，请检查网络后重试')
    expect(describeError(new Error('boom'))).toBe('boom')
    expect(describeError('oops')).toBe('操作失败，请稍后重试')
  })
})

describe('isUnauthorized', () => {
  it('treats missing and expired credentials as unauthorized', () => {
    expect(isUnauthorized(new ApiError(40101, 'unauthorized'))).toBe(true)
    expect(isUnauthorized(new ApiError(40102, 'expired'))).toBe(true)
    expect(isUnauthorized(new ApiError(40302, 'not entitled'))).toBe(false)
    expect(isUnauthorized(new Error('boom'))).toBe(false)
  })
})
