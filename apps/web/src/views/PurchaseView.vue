<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiRequest, ApiError, storeTokens } from '../api/client'
import { getCourse, type CourseCard } from '../api/courses'
import { createOrder, isOrderPaid, payOrder } from '../api/orders'
import { loadProfile } from '../api/auth'
import { describePurchaseError, readTicket, type RedeemResult } from '../utils/purchase'
import { describeError } from '../utils/errors'
import { formatCents } from '../utils/format'
import { submitPaymentForm } from '../utils/payment'
import { signIn } from '../utils/session'

type Phase = 'loading' | 'error' | 'ready' | 'done'

const ticket = readTicket(window.location.search)
const phase = ref<Phase>('loading')
const loadingText = ref('正在验证购买链接…')
const errorText = ref('')
const course = ref<CourseCard | null>(null)
const alreadyOwned = ref(false)
const offline = ref(false)
const orderNo = ref('')
const buying = ref(false)
const notice = ref('')
const orderKey = crypto.randomUUID()
const paymentKey = crypto.randomUUID()
const redeemed = ref(false)
const redeemedCourseId = ref<number | null>(null)

const priceText = computed(() => {
  if (!course.value) {
    return '--'
  }
  return course.value.price === 0 ? '免费' : `¥${formatCents(course.value.price)}`
})

/** Leaves the one-off ticket out of the address bar once it has been consumed. */
function stripTicket(): void {
  window.history.replaceState({}, '', window.location.pathname)
}

/** Shows the failure state with an actionable message. */
function fail(error: unknown): void {
  errorText.value = describePurchaseError(error)
  phase.value = 'error'
}

/**
 * Caches the profile so the header reflects the redeemed session.
 *
 * @param accessToken freshly issued token
 * @param refreshToken freshly issued refresh token
 * @author Caspian
 * @since 0.2.0
 */
async function cacheProfile(accessToken: string, refreshToken: string): Promise<void> {
  try {
    const profile = await loadProfile()
    signIn(accessToken, refreshToken, {
      id: profile.id,
      nickname: profile.nickname,
      avatarUrl: profile.avatarUrl ?? null,
      roles: profile.roles,
    })
  } catch {
    // The purchase flow only needs the tokens; the header falls back to the visitor state.
  }
}

/**
 * Loads the course detail with the freshly issued token.
 *
 * @param courseId course identifier returned by redeem
 * @author Caspian
 * @since 0.1.0
 */
async function loadCourse(courseId: number): Promise<void> {
  phase.value = 'loading'
  loadingText.value = '正在加载课程…'
  try {
    const detail = await getCourse(courseId)
    course.value = detail
    alreadyOwned.value = detail.owned === true
    offline.value = detail.status === 3
    phase.value = 'ready'
  } catch (error) {
    fail(error)
  }
}

/** Redeems the one-off ticket, stores the token pair and loads the course. */
async function redeemAndLoad(): Promise<void> {
  if (!ticket) {
    fail(new ApiError(40001, '链接无效，请回到小程序重新获取'))
    return
  }
  phase.value = 'loading'
  loadingText.value = '正在验证购买链接…'
  try {
    const result = await apiRequest<RedeemResult>(
      '/api/v1/orders/purchase-ticket/redeem',
      { method: 'POST', body: JSON.stringify({ ticket }) },
      false,
    )
    storeTokens(result.accessToken, result.refreshToken)
    stripTicket()
    redeemed.value = true
    redeemedCourseId.value = result.courseId
    await cacheProfile(result.accessToken, result.refreshToken)
    await loadCourse(result.courseId)
  } catch (error) {
    if (error instanceof ApiError && (error.code === 40101 || error.code === 40102)) {
      fail(new ApiError(error.code, '链接已过期或已使用，请回到小程序重新获取'))
      return
    }
    fail(error)
  }
}

/** Retries the failed step without consuming a second ticket. */
async function retry(): Promise<void> {
  errorText.value = ''
  if (redeemed.value && redeemedCourseId.value !== null) {
    await loadCourse(redeemedCourseId.value)
    return
  }
  await redeemAndLoad()
}

/** Creates the order and starts the Alipay payment for it. */
async function buy(): Promise<void> {
  if (!course.value || buying.value) {
    return
  }
  buying.value = true
  notice.value = ''
  try {
    const order = await createOrder(course.value.courseId, orderKey)
    orderNo.value = order.orderNo
    if (isOrderPaid(order)) {
      phase.value = 'done'
      return
    }
    const payment = await payOrder(order.orderNo, paymentKey)
    if (submitPaymentForm(payment)) {
      return
    }
    notice.value = `订单 ${order.orderNo} 已创建，但支付表单缺失，请稍后重试`
  } catch (error) {
    if (error instanceof ApiError && error.code === 61002) {
      alreadyOwned.value = true
      notice.value = '你已拥有该课程'
      return
    }
    notice.value = describeError(error)
  } finally {
    buying.value = false
  }
}

onMounted(() => {
  void redeemAndLoad()
})
</script>

<template>
  <main class="purchase">
    <section
      v-if="phase === 'loading'"
      class="card"
    >
      <p class="muted">
        {{ loadingText }}
      </p>
    </section>

    <section
      v-else-if="phase === 'error'"
      class="card"
    >
      <h1>无法完成购买</h1>
      <p class="muted">
        {{ errorText }}
      </p>
      <button
        type="button"
        class="primary"
        @click="retry"
      >
        {{ redeemed ? '重新加载课程' : '重试' }}
      </button>
    </section>

    <section
      v-else-if="phase === 'done'"
      class="card"
    >
      <h1>购买成功</h1>
      <p class="muted">
        课程已开通，可以开始学习了。
      </p>
      <a
        class="primary link"
        href="/learning/my-courses"
      >去学习</a>
    </section>

    <section
      v-else-if="course"
      class="card"
    >
      <h1>{{ course.title }}</h1>
      <p class="muted">
        {{ course.instructorName || '平台讲师' }} · {{ course.lessonCount }} 课时
      </p>

      <p class="price">
        <span class="now">{{ priceText }}</span>
      </p>

      <p
        v-if="offline"
        class="notice"
      >
        课程已下架，暂时无法购买
      </p>
      <p
        v-else-if="alreadyOwned"
        class="notice"
      >
        你已拥有该课程
        <a
          class="inline-link"
          href="/learning/my-courses"
        >去学习</a>
      </p>
      <p
        v-else-if="notice"
        class="notice"
      >
        {{ notice }}
      </p>

      <button
        v-if="!alreadyOwned && !offline"
        type="button"
        class="primary"
        :disabled="buying"
        @click="buy"
      >
        {{ buying ? '正在创建订单…' : '支付宝支付' }}
      </button>
      <p
        v-if="orderNo && !notice"
        class="muted small"
      >
        订单号：{{ orderNo }}
      </p>
      <p class="muted small">
        支付完成后请返回本页面刷新学习入口。
      </p>
    </section>
  </main>
</template>

<style scoped>
.purchase {
  display: flex;
  justify-content: center;
  padding: 72px 24px;
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  color: #1f2329;
}

.card {
  width: 100%;
  max-width: 480px;
  padding: 32px;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  background: #ffffff;
}

h1 {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 600;
}

.muted {
  margin: 0 0 20px;
  color: #646a73;
  line-height: 1.6;
}

.small {
  margin-top: 12px;
  font-size: 13px;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 0 20px;
}

.now {
  font-size: 26px;
  font-weight: 600;
  color: #ff5b1e;
}

.notice {
  margin: 0 0 20px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f7f8fa;
  color: #1f2329;
}

.inline-link,
.link {
  color: #1664ff;
}

.primary {
  display: inline-block;
  width: 100%;
  padding: 11px 16px;
  border: none;
  border-radius: 6px;
  background: #1664ff;
  color: #ffffff;
  font-size: 15px;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
}

.primary:disabled {
  cursor: progress;
  background: #94b8ff;
}
</style>
