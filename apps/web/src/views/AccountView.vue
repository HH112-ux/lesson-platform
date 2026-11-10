<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import PageState from '../components/PageState.vue'
import {
  applyCancellation,
  getCancellationState,
  withdrawCancellation,
  type AccountCancellationView,
} from '../api/account'
import { navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { isSignedIn } from '../utils/session'

const state = ref<AccountCancellationView | null>(null)
const loading = ref(false)
const needLogin = ref(false)
const errorText = ref('')
const actionError = ref('')
const notice = ref('')
const busy = ref(false)
const remaining = ref(0)
let timer: number | undefined

const coolingOff = computed(() => state.value?.status === 0)

const countdown = computed(() => {
  const total = Math.max(0, remaining.value)
  const pad = (value: number): string => String(Math.floor(value)).padStart(2, '0')
  const clock = `${pad((total % 86400) / 3600)}:${pad((total % 3600) / 60)}:${pad(total % 60)}`
  const days = Math.floor(total / 86400)
  return days > 0 ? `${days} 天 ${clock}` : clock
})

function stopTimer(): void {
  if (timer !== undefined) {
    window.clearInterval(timer)
  }
  timer = undefined
}

/** Starts the countdown from the seconds granted by the server. */
function startTimer(seconds: number): void {
  stopTimer()
  remaining.value = Math.max(0, Math.floor(seconds))
  timer = window.setInterval(() => {
    remaining.value = Math.max(0, remaining.value - 1)
    if (remaining.value === 0) {
      stopTimer()
      void load()
    }
  }, 1000)
}

/** Loads the cancellation state of the signed-in account. */
async function load(): Promise<void> {
  if (!isSignedIn.value) {
    needLogin.value = true
    return
  }
  needLogin.value = false
  loading.value = true
  errorText.value = ''
  try {
    state.value = await getCancellationState()
    if (coolingOff.value) {
      startTimer(state.value.remainingSeconds)
    } else {
      stopTimer()
    }
  } catch (error) {
    state.value = null
    stopTimer()
    errorText.value = describeError(error)
    needLogin.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/** Applies or withdraws after the browser confirmation and refreshes the state. */
async function run(decision: 'apply' | 'withdraw'): Promise<void> {
  busy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    if (decision === 'apply') {
      const view = await applyCancellation()
      state.value = view
      startTimer(view.remainingSeconds)
    } else {
      await withdrawCancellation()
      stopTimer()
      notice.value = '已撤销注销申请，账号恢复正常使用。'
    }
    await load()
  } catch (error) {
    // Blocked applications stay on the page instead of a modal: the reason (unfinished order or
    // refund, 67003) has to remain readable while the user goes off to resolve it.
    actionError.value = describeError(error, decision === 'apply' ? '注销申请提交失败' : '撤销失败')
  } finally {
    busy.value = false
  }
}

function askApply(): void {
  const confirmed = window.confirm(
    '提交后进入 7 天冷静期，期间不能下单或支付；到期后账号将被删除且无法恢复。确认申请注销账号？',
  )
  if (confirmed) {
    void run('apply')
  }
}

function askWithdraw(): void {
  const confirmed = window.confirm('撤销后账号恢复正常使用，本次冷静期倒计时作废。确认撤销注销申请？')
  if (confirmed) {
    void run('withdraw')
  }
}

void load()
onUnmounted(stopTimer)
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <h1 class="title">
        账号与安全
      </h1>
      <PageState
        v-if="needLogin"
        variant="empty"
        title="请先登录"
        message="登录后可以查看与操作账号注销。"
        action-label="去登录"
        @action="navigate('/login?next=%2Faccount')"
      />
      <PageState
        v-else-if="loading"
        variant="loading"
        title="正在读取账号状态…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        title="账号状态读取失败"
        :message="errorText"
        action-label="重试"
        @action="load"
      />
      <template v-else>
        <p
          v-if="actionError"
          class="lp-alert"
          role="alert"
        >
          {{ actionError }}
        </p>
        <p
          v-if="notice"
          class="ok"
          role="status"
        >
          {{ notice }}
        </p>
        <section
          v-if="coolingOff"
          class="lp-card"
        >
          <p class="notice">
            注销冷静期中，暂不可购买
          </p>
          <p class="line">
            距离账号删除还剩 <strong class="countdown">{{ countdown }}</strong>
          </p>
          <p class="line lp-subtle">
            到期后账号将被注销：用户名、手机号、邮箱与微信绑定会被清除，已购课程与订单不再可访问。冷静期内可以正常登录、学习已购内容与申请退款，但不能新建订单或发起支付。
          </p>
          <button
            type="button"
            class="lp-btn"
            :disabled="busy"
            @click="askWithdraw"
          >
            撤销注销申请
          </button>
        </section>
        <section
          v-else
          class="lp-card"
        >
          <p class="line">
            注销申请提交后进入 7 天冷静期，冷静期内可随时撤销，到期后账号与个人信息将被删除且无法恢复。
          </p>
          <p class="line lp-subtle">
            存在进行中的订单或退款时无法提交申请，请先处理完再试。
          </p>
          <button
            type="button"
            class="lp-btn lp-btn--ghost"
            :disabled="busy"
            @click="askApply"
          >
            申请注销账号
          </button>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.title { margin-bottom: 20px; font-size: 24px; }
.notice { margin: 0 0 12px; padding: 10px 12px; border-radius: 8px; background: #fff4e5; color: var(--lp-warning); font-size: 14px; font-weight: 600; }
.line { margin: 0 0 12px; font-size: 14px; line-height: 1.7; }
.countdown { color: var(--lp-danger); font-family: ui-monospace, Menlo, monospace; }
.lp-alert { margin: 0 0 12px; }
.ok { margin: 0 0 12px; padding: 10px 12px; border-radius: var(--lp-radius-sm); background: #eaf6ef; color: var(--lp-success); font-size: 13px; }
.lp-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
