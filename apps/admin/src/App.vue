<script setup lang="ts">
import { buildReviewQueuePage } from './utils/review'

const demoItems = [
  { id: 'demo-1', status: 'pending' as const },
  { id: 'demo-2', status: 'approved' as const },
  { id: 'demo-3', status: 'pending' as const },
]
const summary = buildReviewQueuePage(demoItems)
</script>

<template>
  <main class="page">
    <h1>运营与教师后台</h1>
    <p class="hint">
      后台骨架已就绪，业务页面待后续批次实现。
    </p>

    <dl class="stats">
      <div>
        <dt>待审核</dt>
        <dd>{{ summary.pending }}</dd>
      </div>
      <div>
        <dt>已通过</dt>
        <dd>{{ summary.approved }}</dd>
      </div>
      <div>
        <dt>已驳回</dt>
        <dd>{{ summary.rejected }}</dd>
      </div>
    </dl>

    <p class="hint">
      当前为第 {{ summary.pageNum }} 页，每页 {{ summary.pageSize }} 条。
    </p>
  </main>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 96px auto;
  padding: 0 24px;
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  color: #1f2329;
}

h1 {
  margin: 0 0 12px;
  font-size: 28px;
  font-weight: 600;
}

.hint {
  margin: 0 0 24px;
  color: #646a73;
  line-height: 1.6;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 0 0 24px;
}

.stats div {
  padding: 16px;
  border: 1px solid #d0d3d6;
  border-radius: 6px;
  background: #ffffff;
}

.stats dt {
  margin: 0 0 8px;
  color: #646a73;
  font-size: 14px;
}

.stats dd {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}
</style>
