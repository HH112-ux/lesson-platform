<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listOrders, ORDER_STATUS_PAID } from '../api/orders'
import { currentRoute } from '../router'

type ResultStatus = 'checking' | 'paid' | 'pending' | 'unknown'

const orderNo = currentRoute.value.params.orderNo
const status = ref<ResultStatus>('checking')

const title = computed(() => (status.value === 'paid' ? '支付成功' : '支付结果确认中'))

/**
 * Looks the order up in the buyer's most recent orders.
 *
 * <p>The service exposes orders by id only, while the channel returns to this page with the
 * business order number, so the first page is matched by that number. A dedicated status
 * endpoint arrives with the reconciliation slice.</p>
 *
 * @author Caspian
 * @since 0.4.0
 */
async function refresh(): Promise<void> {
  status.value = 'checking'
  try {
    const page = await listOrders(1, 50)
    const row = page.records.find((record) => record.orderNo === orderNo)
    if (!row) {
      status.value = 'unknown'
      return
    }
    status.value = row.status === ORDER_STATUS_PAID ? 'paid' : 'pending'
  } catch {
    // A failed read never means the payment failed, so the page keeps the neutral message.
    status.value = 'unknown'
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <div class="wrapper">
        <section class="lp-card lp-card-body">
          <h1 class="title">
            {{ title }}
          </h1>
          <p class="lp-muted hint">
            <template v-if="status === 'paid'">
              课程已开通，可以开始学习了。
            </template>
            <template v-else-if="status === 'pending'">
              尚未收到支付结果，完成支付后课程会自动开通。
            </template>
            <template v-else-if="status === 'unknown'">
              暂时无法确认支付结果，请稍后在「我的订单」中查看。
            </template>
            <template v-else>
              正在确认支付结果…
            </template>
          </p>

          <p class="order-no">
            订单号：{{ orderNo }}
          </p>

          <p
            v-if="status !== 'paid'"
            class="lp-alert notice"
          >
            支付结果以服务端回调为准，请稍后在「我的订单」中确认状态；课程开通后可到「我的课程」开始学习。
          </p>

          <div class="actions">
            <a
              class="lp-btn lp-btn--primary lp-btn--block"
              href="/learning/my-courses"
            >去我的课程</a>
            <a
              class="lp-btn lp-btn--block"
              href="/orders"
            >查看订单</a>
            <button
              type="button"
              class="lp-btn lp-btn--block"
              :disabled="status === 'checking'"
              @click="refresh"
            >
              刷新状态
            </button>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.wrapper {
  max-width: 480px;
  margin: 32px auto 0;
}

.title {
  font-size: 22px;
}

.hint {
  margin: 6px 0 20px;
  font-size: 13px;
}

.order-no {
  margin-bottom: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.notice {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
