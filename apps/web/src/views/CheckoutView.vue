<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { getCourse, type CourseCard } from '../api/courses'
import { createOrder, isOrderPaid, payOrder } from '../api/orders'
import { ApiError } from '../api/client'
import { currentRoute, navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'

const courseId = Number(currentRoute.value.params.courseId)
const course = ref<CourseCard | null>(null)
const loading = ref(true)
const errorText = ref('')
const unauthorized = ref(false)
const owned = ref(false)
const paying = ref(false)
const notice = ref('')
const orderKey = crypto.randomUUID()
const paymentKey = crypto.randomUUID()

const priceText = computed(() => {
  if (!course.value) {
    return ''
  }
  return course.value.price === 0 ? '免费' : `¥${formatCents(course.value.price)}`
})

const payLabel = computed(() => (paying.value ? '正在创建订单…' : '支付宝支付'))

/** Loads the course summary shown on the cashier. */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  try {
    const detail = await getCourse(courseId)
    course.value = detail
    owned.value = detail.owned === true
  } catch (error) {
    course.value = null
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/** Creates the order and either finishes it or sends the buyer to the payment channel. */
async function pay(): Promise<void> {
  if (!course.value || paying.value) {
    return
  }
  paying.value = true
  notice.value = ''
  try {
    const order = await createOrder(course.value.courseId, orderKey)
    if (isOrderPaid(order)) {
      navigate(`/orders/${order.orderNo}/result`)
      return
    }
    const payment = await payOrder(order.orderNo, paymentKey)
    const payUrl = payment.payParams?.payUrl
    if (payUrl) {
      window.location.assign(payUrl)
      return
    }
    notice.value = `订单 ${order.orderNo} 已创建，但支付参数缺失，请稍后重试`
  } catch (error) {
    if (error instanceof ApiError && error.code === 61002) {
      owned.value = true
      notice.value = '你已拥有该课程'
      return
    }
    if (isUnauthorized(error)) {
      navigate(`/login?next=${encodeURIComponent(`/checkout/${courseId}`)}`)
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
          v-else-if="course"
          class="lp-card lp-card-body"
        >
          <h1 class="title">
            确认订单
          </h1>
          <p class="lp-muted lp-small hint">
            支付完成后课程会自动开通。
          </p>

          <div class="summary">
            <div class="summary-main">
              <p class="course-title">
                {{ course.title }}
              </p>
              <p class="lp-muted lp-small">
                {{ course.instructorName || '平台讲师' }} · {{ course.lessonCount }} 课时
              </p>
            </div>
            <p class="lp-price price">
              {{ priceText }}
            </p>
          </div>

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

          <button
            v-if="!owned"
            type="button"
            class="lp-btn lp-btn--primary lp-btn--block"
            :disabled="paying"
            @click="pay"
          >
            {{ payLabel }}
          </button>

          <p class="lp-muted lp-small foot">
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
</style>
