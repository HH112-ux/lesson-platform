/**
 * Unit tests for the chart geometry helper.
 *
 * @author Caspian
 * @since 0.21.0
 */
import { describe, expect, it } from 'vitest'
import { buildChartGeometry, niceMax } from './chart'

describe('niceMax', () => {
  it('returns one for empty or negative input', () => {
    expect(niceMax(0)).toBe(1)
    expect(niceMax(-5)).toBe(1)
    expect(niceMax(Number.NaN)).toBe(1)
  })

  it('rounds up to a 1, 2, 2.5 or 5 step', () => {
    expect(niceMax(3)).toBe(4)
    expect(niceMax(40)).toBe(40)
    expect(niceMax(90)).toBe(100)
  })
})

describe('buildChartGeometry', () => {
  const points = [
    { label: '2026-11-01', value: 0 },
    { label: '2026-11-02', value: 50 },
    { label: '2026-11-03', value: 100 },
  ]

  it('places dots and paths inside the plot area', () => {
    const geometry = buildChartGeometry(points, 720, 220)

    expect(geometry.maxValue).toBe(100)
    expect(geometry.dots.map((dot) => dot.x)).toEqual([56, 382, 708])
    expect(geometry.dots.map((dot) => dot.y)).toEqual([194, 105, 16])
    expect(geometry.linePath).toBe('M 56 194 L 382 105 L 708 16')
    expect(geometry.areaPath.endsWith('Z')).toBe(true)
    expect(geometry.ticks).toHaveLength(5)
    expect(geometry.ticks[0].value).toBe(0)
    expect(geometry.ticks[4].value).toBe(100)
  })

  it('gives zero values no bar height', () => {
    const geometry = buildChartGeometry(points, 720, 220)

    expect(geometry.bars[0].height).toBe(0)
    expect(geometry.bars[2].height).toBeGreaterThan(0)
  })

  it('still produces an axis for an empty series', () => {
    const geometry = buildChartGeometry([], 720, 220)

    expect(geometry.maxValue).toBe(1)
    expect(geometry.linePath).toBe('')
    expect(geometry.areaPath).toBe('')
    expect(geometry.dots).toEqual([])
    expect(geometry.bars).toEqual([])
    expect(geometry.ticks).toHaveLength(5)
  })
})
