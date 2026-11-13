import { describe, expect, it } from 'vitest'
import { resolveRoute } from './router'

describe('resolveRoute', () => {
  it('maps the root path to the home page', () => {
    expect(resolveRoute('/').name).toBe('home')
    expect(resolveRoute('').name).toBe('home')
  })

  it('tolerates a trailing slash', () => {
    const route = resolveRoute('/login/')
    expect(route.name).toBe('login')
    expect(route.path).toBe('/login')
  })

  it('extracts the course identifier', () => {
    const route = resolveRoute('/courses/42')
    expect(route.name).toBe('course-detail')
    expect(route.params.courseId).toBe('42')
  })

  it('extracts the lesson identifier', () => {
    const route = resolveRoute('/learning/lessons/7')
    expect(route.name).toBe('player')
    expect(route.params.lessonId).toBe('7')
  })

  it('maps the learning and account pages', () => {
    expect(resolveRoute('/learning/my-courses').name).toBe('my-courses')
    expect(resolveRoute('/orders').name).toBe('orders')
    expect(resolveRoute('/cart').name).toBe('cart')
    expect(resolveRoute('/purchase').name).toBe('purchase')
    expect(resolveRoute('/register').name).toBe('register')
    expect(resolveRoute('/health').name).toBe('health')
  })

  it('maps the cashier and payment result pages', () => {
    expect(resolveRoute('/checkout/9').name).toBe('checkout')
    expect(resolveRoute('/checkout/9').params.courseId).toBe('9')

    const result = resolveRoute('/orders/LP20261106001/result')
    expect(result.name).toBe('order-result')
    expect(result.params.orderNo).toBe('LP20261106001')
  })

  it('parses the query string', () => {
    const route = resolveRoute('/purchase', '?ticket=abc')
    expect(route.query.get('ticket')).toBe('abc')
  })

  it('rejects unknown or malformed paths', () => {
    expect(resolveRoute('/courses/abc').name).toBe('not-found')
    expect(resolveRoute('/learning/lessons').name).toBe('not-found')
    expect(resolveRoute('/unknown').name).toBe('not-found')
  })
})
