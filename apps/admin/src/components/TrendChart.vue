<script setup lang="ts">
/**
 * Inline SVG trend chart used by the operations dashboard.
 *
 * <p>The component deliberately avoids a chart library: the console only needs an area series and
 * an optional bar series, both drawn from the geometry helper so the rendering stays dependency
 * free.
 *
 * @author Caspian
 * @since 0.21.0
 */
import { computed } from 'vue'
import { buildChartGeometry, type ChartPoint } from '../utils/chart'

const props = withDefaults(
  defineProps<{
    /** Values to draw, oldest first. */
    points: ChartPoint[]
    /** Drawing style, an area line chart or bars. */
    kind?: 'area' | 'bar'
    /** Series colour, usually a CSS variable. */
    color?: string
    /** Formats values for the axis ticks and the hover titles. */
    formatValue?: (value: number) => string
    /** Accessible description of the chart. */
    ariaLabel?: string
  }>(),
  {
    kind: 'area',
    color: 'var(--lp-primary)',
    formatValue: (value: number) => String(value),
    ariaLabel: '趋势图',
  },
)

const geometry = computed(() => buildChartGeometry(props.points))

const labelIndexes = computed(() => {
  const total = props.points.length
  const indexes = new Set<number>()
  if (total === 0) {
    return indexes
  }
  const step = Math.max(1, Math.ceil(total / 6))
  for (let index = 0; index < total; index += step) {
    indexes.add(index)
  }
  indexes.add(total - 1)
  return indexes
})

function shortLabel(label: string): string {
  return label.length >= 10 ? label.slice(5) : label
}

function labelX(index: number): number {
  const bar = geometry.value.bars[index]
  if (props.kind === 'bar' && bar) {
    return bar.x + bar.width / 2
  }
  return geometry.value.dots[index]?.x ?? 0
}
</script>

<template>
  <svg
    class="trend-chart"
    :viewBox="`0 0 ${geometry.width} ${geometry.height}`"
    role="img"
    :aria-label="ariaLabel"
  >
    <g class="trend-chart__grid">
      <line
        v-for="(tick, index) in geometry.ticks"
        :key="`grid-${index}`"
        :x1="geometry.plotLeft"
        :x2="geometry.plotLeft + geometry.plotWidth"
        :y1="tick.y"
        :y2="tick.y"
      />
    </g>
    <g class="trend-chart__axis">
      <text
        v-for="(tick, index) in geometry.ticks"
        :key="`tick-${index}`"
        :x="geometry.plotLeft - 8"
        :y="tick.y + 4"
        text-anchor="end"
      >
        {{ formatValue(tick.value) }}
      </text>
    </g>

    <g v-if="kind === 'area'">
      <path
        class="trend-chart__area"
        :d="geometry.areaPath"
        :fill="color"
      />
      <path
        class="trend-chart__line"
        :d="geometry.linePath"
        :stroke="color"
      />
      <circle
        v-for="dot in geometry.dots"
        :key="`dot-${dot.label}`"
        :cx="dot.x"
        :cy="dot.y"
        r="3"
        :fill="color"
      >
        <title>{{ dot.label }}：{{ formatValue(dot.value) }}</title>
      </circle>
    </g>

    <g v-else>
      <rect
        v-for="bar in geometry.bars"
        :key="`bar-${bar.label}`"
        :x="bar.x"
        :y="bar.y"
        :width="bar.width"
        :height="bar.height"
        rx="3"
        :fill="color"
      >
        <title>{{ bar.label }}：{{ formatValue(bar.value) }}</title>
      </rect>
    </g>

    <g class="trend-chart__axis">
      <template
        v-for="(dot, index) in geometry.dots"
        :key="`label-${dot.label}`"
      >
        <text
          v-if="labelIndexes.has(index)"
          :x="labelX(index)"
          :y="geometry.height - 6"
          text-anchor="middle"
        >
          {{ shortLabel(dot.label) }}
        </text>
      </template>
    </g>
  </svg>
</template>

<style scoped>
.trend-chart {
  display: block;
  width: 100%;
  height: auto;
}

.trend-chart__grid line {
  stroke: var(--lp-border);
  stroke-dasharray: 3 4;
}

.trend-chart__axis text {
  fill: var(--lp-muted);
  font-size: 11px;
}

.trend-chart__area {
  opacity: 0.12;
}

.trend-chart__line {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
