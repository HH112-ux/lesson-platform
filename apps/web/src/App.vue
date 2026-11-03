<script setup lang="ts">
import { computed, ref } from 'vue'
import { GATEWAY_HEALTH_PATH } from '@lp/shared'
import { describeGatewayHealth } from './utils/health'

const checking = ref(false)
const reachable = ref<boolean | null>(null)
const summary = computed(() =>
  reachable.value === null ? null : describeGatewayHealth(reachable.value),
)

// Probes the gateway through the Vite proxy; the timeout keeps the page responsive
// when the backend is not running yet.
async function probeGateway(): Promise<void> {
  checking.value = true
  try {
    const response = await fetch(GATEWAY_HEALTH_PATH, {
      signal: AbortSignal.timeout(5000),
    })
    reachable.value = response.ok
  } catch {
    reachable.value = false
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <main class="page">
    <h1>课程平台</h1>
    <p class="hint">
      前端骨架已就绪，业务页面待后续批次实现。
    </p>

    <button
      type="button"
      :disabled="checking"
      @click="probeGateway"
    >
      {{ checking ? 'Checking...' : 'Check gateway health' }}
    </button>

    <p
      v-if="summary"
      class="status"
      :class="{ ok: summary.reachable }"
    >
      {{ summary.label }}
    </p>
  </main>
</template>

<style scoped>
.page {
  max-width: 640px;
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

button {
  padding: 8px 16px;
  border: 1px solid #d0d3d6;
  border-radius: 6px;
  background: #ffffff;
  font-size: 14px;
  cursor: pointer;
}

button:disabled {
  cursor: progress;
  color: #8f959e;
}

.status {
  margin-top: 16px;
  color: #8f959e;
}

.status.ok {
  color: #1f7a4d;
}
</style>
