<script setup lang="ts">
import { computed, ref } from 'vue'
import { GATEWAY_HEALTH_PATH } from '@lp/shared'
import { describeGatewayHealth } from '../utils/health'

const checking = ref(false)
const reachable = ref<boolean | null>(null)
const summary = computed(() =>
  reachable.value === null ? null : describeGatewayHealth(reachable.value),
)

/** Probes the gateway through the Vite proxy, with a timeout for a cold backend. */
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
  <main class="lp-page">
    <div class="lp-container">
      <section class="lp-card lp-card-body">
        <h1 class="title">
          运行状态
        </h1>
        <p class="lp-muted hint">
          用于本地确认前端与网关是否连通。
        </p>

        <button
          type="button"
          class="lp-btn"
          :disabled="checking"
          @click="probeGateway"
        >
          {{ checking ? '检测中…' : '检测网关' }}
        </button>

        <p
          v-if="summary"
          class="status"
          :class="{ ok: summary.reachable }"
        >
          {{ summary.label }}
        </p>
      </section>
    </div>
  </main>
</template>

<style scoped>
.title {
  font-size: 20px;
}

.hint {
  margin: 6px 0 20px;
}

.status {
  margin-top: 16px;
  color: var(--lp-text-subtle);
}

.status.ok {
  color: var(--lp-success);
}
</style>
