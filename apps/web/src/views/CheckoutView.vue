<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { getCourse, type CourseCard } from '../api/courses'
import {
  createCombinedOrder,
  createOrder,
  isOrderPaid,
  payOrder,
  type OrderView,
} from '../api/orders'
import { removeCartItem } from '../api/cart'
import { ApiError } from '../api/client'
import { currentRoute, navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'
import { describePaymentFormRejection, submitPaymentForm } from '../utils/payment'

const MAX_COMBINED_COURSES = 20
const requestCourseIds = parseCourseIds(currentRoute.value.query.get('courseIds'))
const combined = requestCourseIds.length > 1
const singleCourseId = Number(currentRoute.value.params.courseId)
const courses = ref<CourseCard[]>([])
const loading = ref(true)
const errorText = ref('')
const unauthorized = ref(false)
const owned = ref(false)
const paying = ref(false)
const notice = ref('')
const createdOrder = ref<OrderView | null>(null)
const paymentUnavailable = ref(false)
const orderKey = crypto.randomUUID()
const paymentKey = crypto.randomUUID()

const nextPath = combined
  ? `/checkout?courseIds=${requestCourseIds.join(',')}`
  : `/checkout/${singleCourseId}`

const payLabel = computed(() => {
  if (paying.value) {
    return '正在创建订单…'
  }
  return combined ? `合并支付 ${courses.value.length} 门课程` : '支付宝支付'
})

/**
 * Parses the comma separated course ids of a combined checkout.
 *
 * @param raw query value such as {@code 7,9}
 * @returns positive integers without duplicates, empty when the value is absent
 * @author Caspian
 * @since 0.22.0
 */
function parseCourseIds(raw: string | null): number[] {
  if (!raw) {
    return []
  }
  const ids = raw
    .split(',')
    .map((value) => Number(value))
    .filter((value) => Number.isInteger(value) && value > 0)
  return Array.from(new Set(ids))
}

/**
 * Renders one course price for display only.
 *
 * @param item course returned by the catalog
 * @returns a human readable price
 * @author Caspian
 * @since 0.22.0
 */
function priceTextOf(item: CourseCard): string {
  return item.price === 0 ? '免费' : `¥${formatCents(item.price)}`
}

/** Loads the courses shown on the cashier, one or several. */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  const ids = combined ? requestCourseIds : [singleCourseId]
  if (ids.length === 0 || ids.some((id) => !Number.isInteger(id) || id <= 0)) {
    courses.value = []
    errorText.value = '课程信息不完整，请从课程页或购物车重新进入结算。'
    loading.value = false
    return
  }
  if (ids.length > MAX_COMBINED_COURSES) {
    courses.value = []
    errorText.value = `一次最多结算 ${MAX_COMBINED_COURSES} 门课程。`
    loading.value = false
    return
  }
  try {
    courses.value = await Promise.all(ids.map((id) => getCourse(id)))
    owned.value = courses.value.some((item) => item.owned === true)
  } catch (error) {
    courses.value = []
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/** Drops the settled lines from the cart without blocking the cashier. */
async function clearSettledCartLines(): Promise<void> {
  await Promise.all(
    courses.value.map(async (item) => {
      try {
        await removeCartItem(item.courseId)
      } catch {
        // A stale cart line must never block an order that already exists.
      }
    }),
  )
}

/** Creates the order and either finishes it or sends the buyer to the payment channel. */
async function pay(): Promise<void> {
  if (courses.value.length === 0 || paying.value) {
    return
  }
  paying.value = true
  notice.value = ''
  paymentUnavailable.value = false
  try {
    const order = combined
      ? await createCombinedOrder(courses.value.map((item) => item.courseId), orderKey)
      : await createOrder(courses.value[0].courseId, orderKey)
    createdOrder.value = order
    if (combined) {
      void clearSettledCartLines()
    }
    if (isOrderPaid(order)) {
      navigate(`/orders/${order.orderNo}/result`)
      return
    }
    const payment = await payOrder(order.orderNo, paymentKey)
    const rejection = submitPaymentForm(payment)
    if (rejection === null) {
      return
    }
    notice.value = `订单 ${order.orderNo} 已创建，${describePaymentFormRejection(rejection)}`
  } catch (error) {
    if (error instanceof ApiError && error.code === 50205 && createdOrder.value) {
      // The order exists but the channel is not configured; never fake a successful payment.
      paymentUnavailable.value = true
      return
    }
    if (error instanceof ApiError && error.code === 61002) {
      owned.value = true
      notice.value = combined ? '你已拥有其中一门课程' : '你已拥有该课程'
      return
    }
    if (isUnauthorized(error)) {
      navigate(`/login?next=${encodeURIComponent(nextPath)}`)
      return
    }
    notice.value = describeError(error)
  } finally {
    paying.value = false
  }
}

void load()
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <div class="wrapper">
        <PageState
          v-if="loading"
          variant="loading"
          title="正在准备订单…"
        />
        <PageState
          v-else-if="errorText"
          variant="error"
          :title="unauthorized ? '请先登录' : '课程加载失败'"
          :message="errorText"
          action-label="重试"
          @action="load"
        />

        <section
          v-else-if="courses.length"
          class="lp-card lp-card-body"
        >
          <h1 class="title">
            确认订单
          </h1>
          <p class="lp-muted lp-small hint">
            支付完成后课程会自动开通。
          </p>

          <ul
            v-if="combined"
            class="summary summary-lines"
          >
            <li
              v-for="item in courses"
              :key="item.courseId"
            >
              <a
                class="course-title"
                :href="`/courses/${item.courseId}`"
              >{{ item.title }}</a>
              <span class="lp-price line-price">{{ priceTextOf(item) }}</span>
            </li>
          </ul>
          <div
            v-else
            class="summary"
          >
            <div class="summary-main">
              <p class="course-title">
                {{ courses[0].title }}
              </p>
              <p class="lp-muted lp-small">
                {{ courses[0].instructorName || '平台讲师' }} · {{ courses[0].lessonCount }} 课时
              </p>
            </div>
            <p class="lp-price price">
              {{ priceTextOf(courses[0]) }}
            </p>
          </div>
          <p class="lp-muted lp-small total-hint">
            {{ combined ? `共 ${courses.length} 门课程，` : '' }}应付金额以提交订单后服务端返回为准。
          </p>

          <p
            v-if="owned"
            class="notice"
          >
            你已拥有该课程
            <a href="/learning/my-courses">去学习</a>
          </p>
          <p
            v-else-if="notice"
            class="lp-alert notice"
          >
            {{ notice }}
          </p>

          <section
            v-if="paymentUnavailable && createdOrder"
            class="pay-placeholder"
          >
            <div
              class="qr-stub"
              aria-hidden="true"
            >
              <span class="qr-stub-mark">!</span>
              <span class="qr-stub-text">未开通</span>
            </div>
            <h2 class="placeholder-title">
              支付二维码暂未开通
            </h2>
            <p class="lp-muted lp-small">
              当前环境尚未接入真实支付渠道，订单已创建但未支付。
            </p>
            <dl class="placeholder-meta">
              <div v-if="combined">
                <dt>课程</dt>
                <dd>{{ courses.map((item) => item.title).join('、') }}</dd>
              </div>
              <div>
                <dt>订单号</dt>
                <dd>{{ createdOrder.orderNo }}</dd>
              </div>
              <div>
                <dt>应付金额</dt>
                <dd>¥{{ formatCents(createdOrder.payAmountFen) }}</dd>
              </div>
            </dl>
            <p class="placeholder-note">
              如需完成购买，请联系平台客服处理；本页不会伪造支付结果。
            </p>
            <div class="placeholder-actions">
              <a
                class="lp-btn lp-btn--ghost"
                href="/orders"
              >查看订单</a>
              <button
                type="button"
                class="lp-btn lp-btn--primary"
                :disabled="paying"
                @click="pay"
              >
                {{ paying ? '正在重试…' : '重新尝试支付' }}
              </button>
            </div>
          </section>

          <button
            v-if="!owned && !paymentUnavailable"
            type="button"
            class="lp-btn lp-btn--primary lp-btn--block"
            :disabled="paying"
            @click="pay"
          >
            {{ payLabel }}
          </button>

          <p
            v-if="!paymentUnavailable"
            class="lp-muted lp-small foot"
          >
            微信内不支持支付宝支付，请在浏览器中打开本页完成购买。
          </p>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.wrapper {
  max-width: 560px;
  margin: 16px auto 0;
}

.title {
  font-size: 22px;
}

.hint {
  margin: 6px 0 20px;
}

.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  border-radius: var(--lp-radius-sm);
  background: #f7f8fa;
}

.summary-lines {
  display: block;
  margin: 0 0 8px;
  padding: 8px 16px;
  list-style: none;
}

.summary-lines li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
}

.summary-lines li + li {
  border-top: 1px solid var(--lp-border);
}

.line-price {
  white-space: nowrap;
}

.total-hint {
  margin-bottom: 20px;
}

.course-title {
  margin-bottom: 4px;
  font-size: 15px;
  font-weight: 600;
}

.price {
  font-size: 22px;
  white-space: nowrap;
}

.notice {
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: var(--lp-radius-sm);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: 13px;
}

.lp-alert.notice {
  background: var(--lp-danger-soft);
  color: var(--lp-danger);
}

.foot {
  margin-top: 16px;
}

.pay-placeholder {
  margin-top: 8px;
  padding: 20px;
  border: 1px dashed var(--lp-border);
  border-radius: var(--lp-radius-sm);
  text-align: center;
}

.qr-stub {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 132px;
  margin: 0 auto 14px;
  border: 2px dashed #c4c9d2;
  border-radius: var(--lp-radius-sm);
  background: #f3f4f6;
  color: #8a919c;
}

.qr-stub-mark {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}

.qr-stub-text {
  margin-top: 6px;
  font-size: 13px;
}

.placeholder-title {
  margin-bottom: 6px;
  font-size: 18px;
}

.placeholder-meta {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin: 14px 0;
  font-size: 13px;
}

.placeholder-meta dt {
  color: var(--lp-text-muted);
}

.placeholder-meta dd {
  margin: 4px 0 0;
  font-weight: 600;
}

.placeholder-note {
  margin-bottom: 16px;
  color: var(--lp-text-muted);
  font-size: 13px;
}

.placeholder-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
