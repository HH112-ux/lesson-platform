<script setup lang="ts">
/**
 * Operations dashboard of the console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { onMounted, ref } from 'vue'
import { loadOverview, type MetricsOverview } from '../api/admin'
import { describeError } from '../utils/errors'
import { formatPercent } from '../utils/format'

const overview = ref<MetricsOverview | null>(null)
const loading = ref(false)
const errorText = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    overview.value = await loadOverview()
  } catch (error) {
    overview.value = null
    errorText.value = describeError(error, '看板数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <h1>数据看板</h1>
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
    <template v-else-if="overview">
      <dl class="stats">
        <div class="lp-card">
          <dt>成交金额（元）</dt>
          <dd>{{ overview.gmv.toFixed(2) }}</dd>
        </div>
        <div class="lp-card">
          <dt>订单数</dt>
          <dd>{{ overview.orderCount }}</dd>
        </div>
        <div class="lp-card">
          <dt>支付转化率</dt>
          <dd>{{ formatPercent(overview.paidRate) }}</dd>
        </div>
        <div class="lp-card">
          <dt>新增用户</dt>
          <dd>{{ overview.newUsers }}</dd>
        </div>
      </dl>

      <div class="lp-card">
        <h2>热销课程 Top 5</h2>
        <p
          v-if="overview.courseSalesTop5.length === 0"
          class="lp-empty"
        >
          暂无成交数据
        </p>
        <table
          v-else
          class="lp-table"
        >
          <thead>
            <tr>
              <th>课程</th>
              <th>订单数</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="sale in overview.courseSalesTop5"
              :key="sale.courseId"
            >
              <td>{{ sale.title }}</td>
              <td>{{ sale.orderCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 20px;
}

h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0 0 16px;
}

.stats dt {
  margin-bottom: 8px;
  color: var(--lp-muted);
  font-size: 13px;
}

.stats dd {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}
</style>
