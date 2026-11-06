<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { listOrders, type OrderView } from '../api/orders'
import { navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatYuan } from '../utils/format'
import { isSignedIn } from '../utils/session'

const PAGE_SIZE = 10

const orders = ref<OrderView[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')
const needLogin = ref(false)

const pageCount = computed(() => (
  total.value > 0 ? Math.ceil(total.value / PAGE_SIZE) : 0
))

const loginHref = computed(() => (
  `/login?next=${encodeURIComponent('/orders')}`
))

/**
 * Maps an order status code to a label.
 *
 * @param status backend status code
 * @returns Chinese label, falling back to the raw code
 * @author Caspian
 * @since 0.2.0
 */
function statusLabel(status: number): string {
  switch (status) {
    case 0:
      return '待支付'
    case 1:
      return '支付中'
    case 2:
      return '已支付'
    case 3:
      return '已关闭'
    case 4:
      return '已退款'
    default:
      return `状态 ${status}`
  }
}

function statusClass(status: number): string {
  if (status === 2) {
    return 'ok'
  }
  if (status === 3 || status === 4) {
    return 'muted'
  }
  return 'pending'
}

/** Loads one page of the order list. */
async function load(): Promise<void> {
  if (!isSignedIn.value) {
    needLogin.value = true
    return
  }
  needLogin.value = false
  loading.value = true
  errorText.value = ''
  try {
    const page = await listOrders(pageNum.value, PAGE_SIZE)
    orders.value = page.records ?? []
    total.value = page.total ?? orders.value.length
  } catch (error) {
    orders.value = []
    total.value = 0
    errorText.value = describeError(error)
    needLogin.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

function goPage(next: number): void {
  if (next < 1 || (pageCount.value > 0 && next > pageCount.value)) {
    return
  }
  pageNum.value = next
  void load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

void load()
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <section class="head">
        <h1 class="title">
          我的订单
        </h1>
        <a
          class="lp-btn"
          href="/"
        >去选课</a>
      </section>

      <PageState
        v-if="needLogin"
        variant="empty"
        title="请先登录"
        message="登录后可以查看订单状态与金额。"
        action-label="去登录"
        @action="navigate(loginHref)"
      />
      <PageState
        v-else-if="loading"
        variant="loading"
        title="正在加载订单…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        title="订单加载失败"
        :message="errorText"
        action-label="重试"
        @action="load"
      />
      <PageState
        v-else-if="orders.length === 0"
        variant="empty"
        title="暂无订单"
        message="购买课程后，订单会出现在这里。"
        action-label="浏览课程"
        @action="navigate('/')"
      />

      <div
        v-else
        class="lp-card"
      >
        <table class="table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>课程</th>
              <th>金额</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="order in orders"
              :key="order.orderNo"
            >
              <td class="mono">
                {{ order.orderNo }}
              </td>
              <td>
                <a :href="`/courses/${order.courseId}`">课程 {{ order.courseId }}</a>
              </td>
              <td>
                <span class="price">¥{{ formatYuan(order.payAmount) }}</span>
                <span
                  v-if="order.discountAmount > 0"
                  class="lp-small lp-muted"
                >（已优惠 ¥{{ formatYuan(order.discountAmount) }}）</span>
              </td>
              <td>
                <span
                  class="status"
                  :class="statusClass(order.status)"
                >{{ statusLabel(order.status) }}</span>
              </td>
              <td>
                <button
                  type="button"
                  class="lp-btn lp-btn--ghost"
                  disabled
                  title="退款功能未开放"
                >
                  退款
                </button>
                <span class="lp-small lp-subtle">未开放</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div
        v-if="!needLogin && !loading && !errorText && pageCount > 1"
        class="pager"
      >
        <button
          type="button"
          class="lp-btn"
          :disabled="pageNum <= 1"
          @click="goPage(pageNum - 1)"
        >
          上一页
        </button>
        <span class="lp-muted lp-small">第 {{ pageNum }} / {{ pageCount }} 页</span>
        <button
          type="button"
          class="lp-btn"
          :disabled="pageNum >= pageCount"
          @click="goPage(pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </main>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.title {
  font-size: 24px;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--lp-border);
  font-size: 14px;
  text-align: left;
}

.table th {
  color: var(--lp-text-muted);
  font-weight: 500;
}

.table tr:last-child td {
  border-bottom: none;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.price {
  color: var(--lp-price);
  font-weight: 600;
}

.status {
  display: inline-flex;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 20px;
}

.status.ok {
  background: #e8f7ee;
  color: var(--lp-success);
}

.status.pending {
  background: #fff4e5;
  color: var(--lp-warning);
}

.status.muted {
  background: #f2f3f5;
  color: var(--lp-text-muted);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}
</style>
