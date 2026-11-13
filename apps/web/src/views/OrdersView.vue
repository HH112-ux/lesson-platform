<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { applyRefund, listOrders, type OrderSummaryView } from '../api/orders'
import { navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'
import { isSignedIn } from '../utils/session'

const PAGE_SIZE = 10

const orders = ref<OrderSummaryView[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')
const needLogin = ref(false)
const applyingOrder = ref<OrderSummaryView | null>(null)
const refundReason = ref('')
const refundKey = ref('')
const applyError = ref('')
const submitting = ref(false)

const REFUND_STATUS: Record<number, string> = {
  0: '待审核',
  1: '退款处理中',
  2: '已退款',
  3: '已驳回',
  4: '退款失败',
  5: '结果未知待确认',
}

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
      return '已支付'
    case 2:
      return '已取消'
    case 3:
      return '已关闭'
    case 4:
      return '退款中'
    case 5:
      return '已退款'
    default:
      return `状态 ${status}`
  }
}

function statusClass(status: number): string {
  if (status === 1) {
    return 'ok'
  }
  if (status === 2 || status === 3 || status === 5) {
    return 'muted'
  }
  return 'pending'
}

function refundLabel(status: number): string {
  return REFUND_STATUS[status] ?? `退款状态 ${status}`
}

/**
 * Tells whether the buyer may apply or re-apply for a refund.
 *
 * @param order order row
 * @returns true for a paid order without an in-progress refund
 * @author Caspian
 * @since 0.1.0
 */
function canApply(order: OrderSummaryView): boolean {
  if (order.status !== 1) {
    return false
  }
  if (!order.refund) {
    return true
  }
  return order.refund.status === 3 || order.refund.status === 4
}

/**
 * Prepares the refund dialog for one order.
 *
 * @param order paid order
 * @author Caspian
 * @since 0.1.0
 */
function openApply(order: OrderSummaryView): void {
  applyError.value = ''
  refundReason.value = ''
  refundKey.value = crypto.randomUUID()
  applyingOrder.value = order
}

/** Submits the refund application and reloads the list. */
async function submitApply(): Promise<void> {
  if (!applyingOrder.value) {
    return
  }
  const reason = refundReason.value.trim()
  if (!reason) {
    applyError.value = '请填写退款原因'
    return
  }
  if (reason.length > 300) {
    applyError.value = '退款原因不能超过 300 字'
    return
  }
  submitting.value = true
  applyError.value = ''
  try {
    await applyRefund(applyingOrder.value.orderId, reason, refundKey.value)
    applyingOrder.value = null
    await load()
  } catch (error) {
    applyError.value = describeError(error, '退款申请提交失败')
  } finally {
    submitting.value = false
  }
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
                <template v-if="order.items.length">
                  <template
                    v-for="(item, index) in order.items"
                    :key="item.courseId"
                  >
                    <a :href="`/courses/${item.courseId}`">{{ item.title }}</a>
                    <span v-if="index < order.items.length - 1">、</span>
                  </template>
                </template>
                <span
                  v-else
                  class="lp-subtle"
                >--</span>
              </td>
              <td>
                <span class="price">¥{{ formatCents(order.payAmountFen) }}</span>
              </td>
              <td>
                <span
                  class="status"
                  :class="statusClass(order.status)"
                >{{ statusLabel(order.status) }}</span>
              </td>
              <td class="ops">
                <div
                  v-if="order.refund"
                  class="refund"
                >
                  <span class="refund-state">{{ refundLabel(order.refund.status) }}</span>
                  <span
                    v-if="order.refund.status === 3 && order.refund.reviewReason"
                    class="refund-note"
                  >驳回原因：{{ order.refund.reviewReason }}</span>
                  <span
                    v-else-if="(order.refund.status === 4 || order.refund.status === 5) && order.refund.failReason"
                    class="refund-note"
                  >失败原因：{{ order.refund.failReason }}</span>
                </div>
                <button
                  v-if="canApply(order)"
                  type="button"
                  class="lp-btn lp-btn--ghost"
                  @click="openApply(order)"
                >
                  {{ order.refund ? '重新申请退款' : '申请退款' }}
                </button>
                <span
                  v-else-if="!order.refund"
                  class="lp-small lp-subtle"
                >--</span>
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

      <div
        v-if="applyingOrder"
        class="dialog-mask"
        @click.self="applyingOrder = null"
      >
        <div
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="refund-dialog-title"
        >
          <h2
            id="refund-dialog-title"
            class="dialog-title"
          >
            申请退款
          </h2>
          <p class="dialog-line">
            订单 {{ applyingOrder.orderNo }} · 退款金额 ¥{{ formatCents(applyingOrder.payAmountFen) }}
          </p>
          <p class="dialog-line lp-subtle">
            仅支持全额退款：需在付款后 7 天内且学习进度不超过 20%，提交后由运营审核。
          </p>
          <label class="field">
            <span class="lp-small">退款原因</span>
            <textarea
              v-model="refundReason"
              class="lp-input dialog-textarea"
              rows="3"
              maxlength="300"
              placeholder="请说明退款原因（必填，最多 300 字）"
            />
          </label>
          <p
            v-if="applyError"
            class="action-error"
          >
            {{ applyError }}
          </p>
          <div class="dialog-actions">
            <button
              type="button"
              class="lp-btn"
              :disabled="submitting"
              @click="applyingOrder = null"
            >
              取消
            </button>
            <button
              type="button"
              class="lp-btn lp-btn--primary"
              :disabled="submitting"
              @click="submitApply"
            >
              {{ submitting ? '提交中…' : '提交申请' }}
            </button>
          </div>
        </div>
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

.ops {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.refund {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.refund-state {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.refund-note {
  color: var(--lp-warning);
  font-size: 12px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 18, 22, 0.45);
  z-index: 50;
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 420px;
  padding: 20px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 18, 22, 0.18);
}

.dialog-title {
  font-size: 18px;
}

.dialog-line {
  margin: 0;
  font-size: 13px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dialog-textarea {
  resize: vertical;
  font-family: inherit;
  line-height: 1.6;
}

.action-error {
  margin: 0;
  color: var(--lp-danger);
  font-size: 13px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}
</style>
