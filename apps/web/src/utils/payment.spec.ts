import { describe, expect, it } from 'vitest'
import { describePaymentFormRejection, isTrustedFormAction } from './payment'

describe('isTrustedFormAction', () => {
  it('accepts the production and sandbox Alipay gateways', () => {
    expect(isTrustedFormAction('https://openapi.alipay.com/gateway.do')).toBe(true)
    expect(isTrustedFormAction('https://openapi-sandbox.dl.alipaydev.com/gateway.do')).toBe(true)
  })

  it('rejects relative actions and plain http', () => {
    expect(isTrustedFormAction('')).toBe(false)
    expect(isTrustedFormAction('/gateway.do')).toBe(false)
    expect(isTrustedFormAction('http://openapi.alipay.com/gateway.do')).toBe(false)
  })

  it('rejects script schemes a tampered payload could carry', () => {
    expect(isTrustedFormAction('javascript:alert(1)')).toBe(false)
    expect(isTrustedFormAction('data:text/html,<form>')).toBe(false)
  })
})

describe('describePaymentFormRejection', () => {
  it('never tells the buyer the payment succeeded', () => {
    expect(describePaymentFormRejection('missing')).toContain('支付表单缺失')
    expect(describePaymentFormRejection('malformed')).toContain('结构异常')
    expect(describePaymentFormRejection('untrusted')).toContain('不受信')
  })
})
