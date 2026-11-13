import { describe, expect, it } from 'vitest'
import { formatDuration } from './format'

describe('formatDuration', () => {
  it('keeps short durations in seconds', () => {
    expect(formatDuration(0)).toBe('0 秒')
    expect(formatDuration(59)).toBe('59 秒')
  })

  it('switches to minutes and keeps the rest when it is not zero', () => {
    expect(formatDuration(60)).toBe('1 分')
    expect(formatDuration(510)).toBe('8 分 30 秒')
  })

  it('switches to hours above one hour', () => {
    expect(formatDuration(3600)).toBe('1 小时 0 分')
    expect(formatDuration(11520)).toBe('3 小时 12 分')
  })

  it('floors fractional seconds', () => {
    expect(formatDuration(90.9)).toBe('1 分 30 秒')
  })

  it('reports missing and invalid values as a placeholder', () => {
    expect(formatDuration(null)).toBe('--')
    expect(formatDuration(undefined)).toBe('--')
    expect(formatDuration(Number.NaN)).toBe('--')
    expect(formatDuration(-1)).toBe('--')
  })
})
