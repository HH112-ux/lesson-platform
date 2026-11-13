/**
 * Pure geometry helpers for the inline SVG charts of the operations console.
 *
 * <p>Keeping the math out of the component makes the scales testable and keeps the template free of
 * arithmetic. Every coordinate is expressed in the chart viewBox so the SVG scales with its card.
 *
 * @author Caspian
 * @since 0.21.0
 */

/** One labelled value plotted by the chart. */
export interface ChartPoint {
  /** Axis label, usually a {@code yyyy-MM-dd} day. */
  label: string
  /** Non negative value. */
  value: number
}

/** Horizontal grid line and its value. */
export interface ChartTick {
  y: number
  value: number
}

/** Computed position of a value in the plot area. */
export interface ChartDot {
  x: number
  y: number
  label: string
  value: number
}

/** Computed bar rectangle of a value in the plot area. */
export interface ChartBar {
  x: number
  y: number
  width: number
  height: number
  label: string
  value: number
}

/** Everything the SVG template needs to draw one chart. */
export interface ChartGeometry {
  width: number
  height: number
  plotLeft: number
  plotTop: number
  plotWidth: number
  plotHeight: number
  maxValue: number
  linePath: string
  areaPath: string
  dots: ChartDot[]
  bars: ChartBar[]
  ticks: ChartTick[]
}

const PADDING = { top: 16, right: 12, bottom: 26, left: 56 }

const TICK_COUNT = 4

/**
 * Rounds a positive maximum up to a 1, 2 or 5 step so the axis stays readable.
 *
 * @param value largest raw value, missing or negative values fall back to {@code 1}
 * @returns rounded axis maximum, always greater than zero
 * @author Caspian
 * @since 0.21.0
 */
export function niceMax(value: number): number {
  if (!Number.isFinite(value) || value <= 0) {
    return 1
  }
  const roughStep = value / TICK_COUNT
  const magnitude = 10 ** Math.floor(Math.log10(roughStep))
  const normalized = roughStep / magnitude
  const step =
    normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10
  return step * magnitude * TICK_COUNT
}

function round(value: number): number {
  return Math.round(value * 100) / 100
}

function safeValue(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0
}

/**
 * Builds the geometry of one chart.
 *
 * @param points labelled values, oldest first
 * @param width viewBox width
 * @param height viewBox height
 * @returns coordinates for grid lines, the line or area path, dots and bars
 * @author Caspian
 * @since 0.21.0
 */
export function buildChartGeometry(
  points: ChartPoint[],
  width = 720,
  height = 220,
): ChartGeometry {
  const plotLeft = PADDING.left
  const plotTop = PADDING.top
  const plotWidth = Math.max(width - PADDING.left - PADDING.right, 1)
  const plotHeight = Math.max(height - PADDING.top - PADDING.bottom, 1)
  const baseline = plotTop + plotHeight
  const maxValue = niceMax(Math.max(...points.map((point) => safeValue(point.value))))
  const stepX = points.length <= 1 ? 0 : plotWidth / (points.length - 1)

  const dots = points.map((point, index) => {
    const value = safeValue(point.value)
    return {
      x: plotLeft + (points.length <= 1 ? plotWidth / 2 : index * stepX),
      y: baseline - (value / maxValue) * plotHeight,
      label: point.label,
      value,
    }
  })

  const linePath =
    dots.length === 0
      ? ''
      : dots
          .map((dot, index) => `${index === 0 ? 'M' : 'L'} ${round(dot.x)} ${round(dot.y)}`)
          .join(' ')
  const areaPath =
    dots.length === 0
      ? ''
      : `${linePath} L ${round(dots[dots.length - 1].x)} ${round(baseline)} L ${round(dots[0].x)} ${round(baseline)} Z`

  const bandwidth = plotWidth / Math.max(points.length, 1)
  const barWidth = Math.max(Math.min(bandwidth * 0.62, 24), 1)
  const bars = points.map((point, index) => {
    const value = safeValue(point.value)
    const barHeight = value <= 0 ? 0 : Math.max((value / maxValue) * plotHeight, 1)
    return {
      x: plotLeft + index * bandwidth + (bandwidth - barWidth) / 2,
      y: baseline - barHeight,
      width: barWidth,
      height: barHeight,
      label: point.label,
      value,
    }
  })

  const ticks = Array.from({ length: TICK_COUNT + 1 }, (_, index) => ({
    y: baseline - (index / TICK_COUNT) * plotHeight,
    value: (maxValue * index) / TICK_COUNT,
  }))

  return {
    width,
    height,
    plotLeft,
    plotTop,
    plotWidth,
    plotHeight,
    maxValue,
    linePath,
    areaPath,
    dots,
    bars,
    ticks,
  }
}
