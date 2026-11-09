<script setup lang="ts">
/**
 * Refund review queue of the operations console.
 *
 * <p>Applying for a refund never moves money; an operator must approve before the channel is
 * called. Rejections require a reason, which is stored on the refund and shown to the student.
 *
 * @author Caspian
 * @since 0.1.0
 */
import { onMounted, ref } from 'vue'
import { listRefunds, reviewRefund, type AdminRefundView } from '../api/admin'
import { describeError } from '../utils/errors'
import { formatFen, formatTime } from '../utils/format'

const PAGE_SIZE = 20

const REFUND_STATUS: Record<number, string> = {
  0: '待审核',
  1: '审核通过（退款中）',
  2: '已退款',
  3: '已驳回',
  4: '退款失败',
  5: '结果未知待确认',
}

const statusFilter = ref<number | undefined>(undefined)
const records = ref<AdminRefundView[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')
const actionError = ref('')
const busyId = ref<number | null>(null)
const rejecting = ref<AdminRefundView | null>(null)
const rejectReason = ref('')

function statusText(status: number): string {
  return REFUND_STATUS[status] ?? `未知(${status})`
}

function channelText(channel: number): string {
  if (channel === 1) {
    return '支付宝'
  }
  if (channel === 2) {
    return '微信'
  }
  return `渠道 ${channel}`
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await listRefunds(statusFilter.value, pageNum.value, PAGE_SIZE)
    records.value = page.records
    total.value = page.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = describeError(error, '退款列表加载失败')
  } finally {
    loading.value = false
  }
}

function changeFilter(): void {
  pageNum.value = 1
  void load()
}

function changePage(next: number): void {
  if (next < 1) {
    return
  }
  pageNum.value = next
  void load()
}

async function approve(refund: AdminRefundView): Promise<void> {
  if (!window.confirm(`确认通过退款 ${refund.refundNo}？通过后将立即调用${channelText(refund.channel)}退款。`)) {
    return
  }
  await submit(refund.refundId, true, '')
}

function openReject(refund: AdminRefundView): void {
  actionError.value = ''
  rejectReason.value = ''
  rejecting.value = refund
}

async function submitReject(): Promise<void> {
  if (!rejecting.value) {
    return
  }
  if (!rejectReason.value.trim()) {
    actionError.value = '驳回必须填写原因'
    return
  }
  await submit(rejecting.value.refundId, false, rejectReason.value.trim())
}

async function submit(refundId: number, approved: boolean, rejectReasonText: string): Promise<void> {
  busyId.value = refundId
  actionError.value = ''
  try {
    await reviewRefund(refundId, {
      approved,
      rejectReason: approved ? undefined : rejectReasonText,
    })
    rejecting.value = null
    await load()
  } catch (error) {
    actionError.value = describeError(error, approved ? '审核通过失败' : '驳回失败')
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <h1>退款审核</h1>
      <label class="filter">
        状态
        <select
          v-model="statusFilter"
          class="lp-input"
          @change="changeFilter"
        >
          <option :value="undefined">
            全部
          </option>
          <option :value="0">
            待审核
          </option>
          <option :value="1">
            审核通过（退款中）
          </option>
          <option :value="2">
            已退款
          </option>
          <option :value="3">
            已驳回
          </option>
          <option :value="4">
            退款失败
          </option>
          <option :value="5">
            结果未知待确认
          </option>
        </select>
      </label>
    </header>

    <p
      v-if="actionError"
      class="action-error"
    >
      {{ actionError }}
    </p>

    <div class="lp-card table-card">
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
      <p
        v-else-if="records.length === 0"
        class="lp-empty"
      >
        没有匹配的退款申请
      </p>
      <table
        v-else
        class="lp-table"
      >
        <thead>
          <tr>
            <th>退款单号</th>
            <th>订单号</th>
            <th>用户 ID</th>
            <th>渠道</th>
            <th>金额（元）</th>
            <th>申请原因</th>
            <th>状态</th>
            <th>申请时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="refund in records"
            :key="refund.refundId"
          >
            <td>{{ refund.refundNo }}</td>
            <td>{{ refund.orderNo }}</td>
            <td>{{ refund.userId }}</td>
            <td>{{ channelText(refund.channel) }}</td>
            <td>¥{{ formatFen(refund.amountFen) }}</td>
            <td>{{ refund.reason }}</td>
            <td>
              {{ statusText(refund.status) }}
              <small
                v-if="refund.status === 3 && refund.reviewReason"
                class="detail"
              >驳回原因：{{ refund.reviewReason }}</small>
              <small
                v-if="(refund.status === 4 || refund.status === 5) && refund.failReason"
                class="detail"
              >失败原因：{{ refund.failReason }}</small>
            </td>
            <td>{{ formatTime(refund.createdAt) }}</td>
            <td class="actions">
              <template v-if="refund.status === 0">
                <button
                  class="lp-btn lp-btn--primary"
                  :disabled="busyId === refund.refundId"
                  @click="approve(refund)"
                >
                  通过
                </button>
                <button
                  class="lp-btn"
                  :disabled="busyId === refund.refundId"
                  @click="openReject(refund)"
                >
                  驳回
                </button>
              </template>
              <span
                v-else
                class="lp-muted"
              >已处理</span>
            </td>
          </tr>
        </tbody>
      </table>

      <footer
        v-if="!loading && !errorText && total > PAGE_SIZE"
        class="pager"
      >
        <button
          class="lp-btn"
          :disabled="pageNum <= 1"
          @click="changePage(pageNum - 1)"
        >
          上一页
        </button>
        <span class="lp-muted">第 {{ pageNum }} 页 / 共 {{ Math.ceil(total / PAGE_SIZE) }} 页</span>
        <button
          class="lp-btn"
          :disabled="pageNum * PAGE_SIZE >= total"
          @click="changePage(pageNum + 1)"
        >
          下一页
        </button>
      </footer>
    </div>

    <div
      v-if="rejecting"
      class="dialog-mask"
    >
      <div class="dialog lp-card">
        <h2>驳回退款</h2>
        <p class="lp-muted">
          退款单 {{ rejecting.refundNo }}，驳回后学员权益保持不变，学员可重新申请。
        </p>
        <textarea
          v-model="rejectReason"
          class="lp-input"
          rows="4"
          maxlength="300"
          placeholder="请填写驳回原因（必填，1-300 字）"
        />
        <p
          v-if="actionError"
          class="action-error"
        >
          {{ actionError }}
        </p>
        <div class="dialog-actions">
          <button
            class="lp-btn"
            :disabled="busyId !== null"
            @click="rejecting = null"
          >
            取消
          </button>
          <button
            class="lp-btn lp-btn--primary"
            :disabled="busyId !== null"
            @click="submitReject"
          >
            确认驳回
          </button>
        </div>
      </div>
    </div>
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

.filter {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.filter .lp-input {
  width: 180px;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.detail {
  display: block;
  margin-top: 4px;
  color: #8a919f;
}

.actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
}

.action-error {
  margin: 0 0 12px;
  color: #d14343;
  font-size: 13px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.dialog {
  width: 420px;
}

.dialog h2 {
  margin: 0 0 8px;
  font-size: 17px;
}

.dialog textarea {
  width: 100%;
  margin-top: 12px;
  box-sizing: border-box;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
