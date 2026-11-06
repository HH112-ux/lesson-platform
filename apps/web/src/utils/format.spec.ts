import { describe, expect, it } from 'vitest'
import { formatCents, formatPercent, formatYuan } from './format'

describe('formatCents', () => {
  it('converts cents to yuan', () => {
    expect(formatCents(9900)).toBe('99.00')
    expect(formatCents(1)).toBe('0.01')
    expect(formatCents(0)).toBe('0.00')
  })

  it('returns a placeholder for missing values', () => {
    expect(formatCents(null)).toBe('--')
    expect(formatCents(undefined)).toBe('--')
  })
})

describe('formatYuan', () => {
  it('keeps two decimals', () => {
    expect(formatYuan(12.5)).toBe('12.50')
    expect(formatYuan(null)).toBe('--')
  })
})

describe('formatPercent', () => {
  it('rounds and clamps the value', () => {
    expect(formatPercent(42.6)).toBe('43%')
    expect(formatPercent(-5)).toBe('0%')
    expect(formatPercent(120)).toBe('100%')
    expect(formatPercent(undefined)).toBe('0%')
  })
})
