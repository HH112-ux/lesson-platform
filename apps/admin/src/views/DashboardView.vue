<script setup lang="ts">
/**
 * Graphical operations dashboard of the console.
 *
 * <p>Every figure comes from the aggregated backend endpoint, never from a hard coded sample. The
 * window is fixed to thirty days because the console has no date picker yet.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { computed, onMounted, ref } from 'vue'
import { loadDashboard, type DashboardMetrics } from '../api/admin'
import TrendChart from '../components/TrendChart.vue'
import { describeError } from '../utils/errors'
import { formatFen, formatPercent } from '../utils/format'

/** Window shown by the dashboard, in days. */
const WINDOW_DAYS = 30

const metrics = ref<DashboardMetrics | null>(null)
const loading = ref(false)
const errorText = ref('')

const revenuePoints = computed(() =>
  (metrics.value?.dailyTrend ?? []).map((day) => ({
    label: day.date,
    value: day.revenueFen,
  })),
)

const orderPoints = computed(() =>
  (metrics.value?.dailyTrend ?? []).map((day) => ({
    label: day.date,
    value: day.orderCount,
  })),
)

const maxCourseSales = computed(() =>
  Math.max(0, ...(metrics.value?.courseSales ?? []).map((sale) => sale.orderCount)),
)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    metrics.value = await loadDashboard(WINDOW_DAYS)
  } catch (error) {
    metrics.value = null
    errorText.value = describeError(error, '看板数据加载失败')
  } finally {
    loading.value = false
  }
}

function formatCount(value: number): string {
  return Number.isFinite(value) ? Math.round(value).toLocaleString('zh-CN') : '--'
}

function formatFenCompact(fen: number): string {
  if (!Number.isFinite(fen)) {
    return '--'
  }
  const yuan = fen / 100
  if (yuan >= 10000) {
    return `${(yuan / 10000).toFixed(1)}万`
  }
  if (yuan >= 1000) {
    return String(Math.round(yuan))
  }
  return yuan.toFixed(2)
}

function rankWidth(orderCount: number): string {
  if (maxCourseSales.value <= 0) {
    return '0%'
  }
  return `${Math.max((orderCount / maxCourseSales.value) * 100, 2).toFixed(1)}%`
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>数据看板</h1>
        <p class="page-sub">
          近 {{ WINDOW_DAYS }} 天 · 数据来自订单、账号与课程库的实时聚合
        </p>
      </div>
      <button
        class="lp-btn"
        :disabled="loading"
        @click="load"
      >
        刷新
      </button>
    </header>

    <p
      v-if="loading"
      class="lp-empty"
    >
      加载中…
    </p>
    <p
      v-else-if="errorText"
      class="lp-empty"
    >
      {{ errorText }}
    </p>
    <template v-else-if="metrics">
      <div class="kpis">
        <article class="lp-card kpi">
          <p class="kpi-label">
            成交额（GMV）
          </p>
          <p class="kpi-value">
            ¥{{ formatFen(metrics.revenueFen) }}
          </p>
          <p class="kpi-hint">
            已支付 {{ formatCount(metrics.paidOrderCount) }} 笔 · 按下单日归集，不扣退款
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            净收入
          </p>
          <p class="kpi-value">
            ¥{{ formatFen(metrics.netRevenueFen) }}
          </p>
          <p class="kpi-hint">
            成交额 − 窗口内退款成功 ¥{{ formatFen(metrics.refundAmountFen) }} · 按退款成功日归集
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            订单
          </p>
          <p class="kpi-value">
            {{ formatCount(metrics.orderCount) }}
          </p>
          <p class="kpi-hint">
            已支付 {{ formatCount(metrics.paidOrderCount) }} 笔 · 转化率
            {{ formatPercent(metrics.paidRate) }}
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            学员
          </p>
          <p class="kpi-value">
            {{ formatCount(metrics.studentTotal) }}
          </p>
          <p class="kpi-hint">
            近 {{ metrics.days }} 天新增 {{ formatCount(metrics.newStudents) }} 人
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            课程
          </p>
          <p class="kpi-value">
            {{ formatCount(metrics.courseTotal) }}
          </p>
          <p class="kpi-hint">
            已上架 {{ formatCount(metrics.coursePublished) }} · 待审核
            {{ formatCount(metrics.coursePendingReview) }}
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            退款申请
          </p>
          <p class="kpi-value">
            {{ formatCount(metrics.refundAppliedCount) }}
          </p>
          <p class="kpi-hint">
            待审核 {{ formatCount(metrics.refundPendingCount) }} · 处理中
            {{ formatCount(metrics.refundProcessingCount) }} · 已退款
            {{ formatCount(metrics.refundedCount) }} 笔 / ¥{{ formatFen(metrics.refundAmountFen) }}
          </p>
        </article>
      </div>

      <div class="charts">
        <section class="lp-card">
          <header class="chart-head">
            <h2>成交额趋势</h2>
            <span>近 {{ metrics.days }} 天 · 按下单日 · 单位元</span>
          </header>
          <TrendChart
            :points="revenuePoints"
            kind="area"
            color="var(--lp-primary)"
            :format-value="formatFenCompact"
            :aria-label="`近 ${WINDOW_DAYS} 天成交额趋势`"
          />
        </section>

        <section class="lp-card">
          <header class="chart-head">
            <h2>订单趋势</h2>
            <span>近 {{ metrics.days }} 天 · 按订单创建日 · 单位笔</span>
          </header>
          <TrendChart
            :points="orderPoints"
            kind="bar"
            color="var(--lp-success)"
            :format-value="formatCount"
            :aria-label="`近 ${WINDOW_DAYS} 天订单趋势`"
          />
        </section>
      </div>

      <section class="lp-card">
        <header class="chart-head">
          <h2>课程销量排行</h2>
          <span>近 {{ metrics.days }} 天已支付订单 · Top 10</span>
        </header>
        <ol
          v-if="metrics.courseSales.length > 0"
          class="ranking"
        >
          <li
            v-for="(sale, index) in metrics.courseSales"
            :key="sale.courseId"
            class="ranking-row"
          >
            <span
              class="ranking-rank"
              :class="{ 'ranking-rank--top': index < 3 }"
            >
              {{ index + 1 }}
            </span>
            <div class="ranking-body">
              <div class="ranking-head">
                <span class="ranking-title">{{ sale.title || '未命名课程' }}</span>
                <span class="ranking-value">
                  {{ formatCount(sale.orderCount) }} 笔 · ¥{{ formatFen(sale.revenueFen) }}
                </span>
              </div>
              <div
                class="ranking-track"
                aria-hidden="true"
              >
                <span
                  class="ranking-fill"
                  :style="{ width: rankWidth(sale.orderCount) }"
                />
              </div>
            </div>
          </li>
        </ol>
        <p
          v-else
          class="lp-empty"
        >
          近 {{ metrics.days }} 天暂无已支付订单
        </p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-sub {
  margin: 6px 0 0;
  color: var(--lp-muted);
  font-size: 13px;
}

h1 {
  margin: 0;
  font-size: 20px;
}

h2 {
  margin: 0;
  font-size: 15px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-label {
  margin: 0 0 8px;
  color: var(--lp-muted);
  font-size: 13px;
}

.kpi-value {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  margin: 0;
  color: var(--lp-muted);
  font-size: 12px;
  line-height: 1.6;
}

.charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.chart-head span {
  color: var(--lp-muted);
  font-size: 12px;
}

.ranking {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ranking-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.ranking-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #f0f1f2;
  color: var(--lp-muted);
  font-size: 12px;
  font-weight: 600;
}

.ranking-rank--top {
  background: #eef3ff;
  color: var(--lp-primary);
}

.ranking-body {
  flex: 1;
  min-width: 0;
}

.ranking-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 13px;
}

.ranking-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-value {
  color: var(--lp-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.ranking-track {
  overflow: hidden;
  height: 6px;
  border-radius: 999px;
  background: #f0f1f2;
}

.ranking-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--lp-primary);
}
</style>
