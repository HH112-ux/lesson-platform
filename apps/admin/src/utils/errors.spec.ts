import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { describeLoginError, describeSignInError } from './errors'

describe('describeLoginError', () => {
  it('reports wrong credentials instead of an expired session', () => {
    const notice = describeLoginError(new ApiError(40101, 'missing credentials or identity'))

    expect(notice).toBe('账号或密码不正确')
    expect(notice).not.toContain('登录已失效')
  })

  it('does not report a body-less HTTP 401 as a credentials error', () => {
    expect(describeLoginError(new ApiError(401, '服务暂时不可用，请稍后重试'))).toBe(
      '服务暂时不可用，请稍后重试',
    )
  })

  it('asks the operator to slow down when login is throttled', () => {
    expect(describeLoginError(new ApiError(42901, 'too many requests'))).toBe('登录尝试过于频繁，请稍后重试')
    expect(describeLoginError(new ApiError(429, '服务暂时不可用，请稍后重试'))).toBe('登录尝试过于频繁，请稍后重试')
  })

  it('keeps server failures distinct from credentials errors', () => {
    expect(describeLoginError(new ApiError(50001, 'internal server error'))).toBe('服务暂时不可用，请稍后重试')
    expect(describeLoginError(new ApiError(502, '服务暂时不可用，请稍后重试'))).toBe('服务暂时不可用，请稍后重试')
  })

  it('reuses the shared wording for login codes that already have one', () => {
    expect(describeLoginError(new ApiError(40301, 'account disabled'))).toBe('当前账号没有该操作权限')
    expect(describeLoginError(new ApiError(67001, 'account has been cancelled'))).toBe('账号已注销，无法登录')
  })

  it('explains a transport failure when no response body arrived', () => {
    expect(describeLoginError(new ApiError(0, '网络连接失败，请检查网络后重试'))).toBe(
      '网络连接失败，请检查网络后重试',
    )
  })
})

describe('describeSignInError', () => {
  it('reports bad credentials while the credential check is running', () => {
    expect(describeSignInError(new ApiError(40101, 'missing credentials or identity'), false)).toBe(
      '账号或密码不正确',
    )
  })

  it('keeps session and service wording once the credentials were accepted', () => {
    expect(describeSignInError(new ApiError(40101, 'missing credentials or identity'), true)).toBe(
      '登录已失效，请重新登录',
    )
    expect(describeSignInError(new ApiError(50001, 'internal server error'), true)).toBe(
      '服务暂时不可用，请稍后重试',
    )
  })

  it('reports throttling on the credential check', () => {
    expect(describeSignInError(new ApiError(42901, 'too many requests'), false)).toBe(
      '登录尝试过于频繁，请稍后重试',
    )
  })
})
