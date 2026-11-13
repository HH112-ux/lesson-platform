<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { getCart, removeCartItem, type CartItemView } from '../api/cart'
import { navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatYuan } from '../utils/format'
import { isSignedIn } from '../utils/session'

const loading = ref(true)
const errorText = ref('')
const unauthorized = ref(false)
const items = ref<CartItemView[]>([])
const selected = ref<number[]>([])
const removing = ref<number[]>([])
const notice = ref('')

const selectedCount = computed(() => selected.value.length)
const allSelected = computed(
  () => items.value.length > 0 && selected.value.length === items.value.length,
)

/** Loads the server side cart and selects every line by default. */
async function load(): Promise<void> {
  if (!isSignedIn.value) {
    loading.value = false
    return
  }
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  try {
    const cart = await getCart()
    items.value = cart.items ?? []
    selected.value = items.value.map((item) => item.courseId)
  } catch (error) {
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

function isSelected(courseId: number): boolean {
  return selected.value.includes(courseId)
}

function toggle(courseId: number): void {
  selected.value = isSelected(courseId)
    ? selected.value.filter((id) => id !== courseId)
    : [...selected.value, courseId]
}

function toggleAll(): void {
  selected.value = allSelected.value ? [] : items.value.map((item) => item.courseId)
}

/** Removes one line and drops it from the selection. */
async function remove(courseId: number): Promise<void> {
  if (removing.value.includes(courseId)) {
    return
  }
  removing.value = [...removing.value, courseId]
  notice.value = ''
  try {
    await removeCartItem(courseId)
    items.value = items.value.filter((item) => item.courseId !== courseId)
    selected.value = selected.value.filter((id) => id !== courseId)
  } catch (error) {
    if (isUnauthorized(error)) {
      navigate('/login?next=%2Fcart')
      return
    }
    notice.value = describeError(error)
  } finally {
    removing.value = removing.value.filter((id) => id !== courseId)
  }
}

/** Opens the single course cashier until the multi course checkout decision is confirmed. */
function checkout(): void {
  if (selected.value.length === 0) {
    return
  }
  if (selected.value.length === 1) {
    navigate(`/checkout/${selected.value[0]}`)
    return
  }
  notice.value = '多门课程合并结算方案确认中，请先选择一门课程结算。'
}

void load()
</script>

<template>
  <main class="lp-page">
    <div class="lp-container cart-page">
      <div class="page-head">
        <h1 class="title">
          购物车
        </h1>
        <p class="lp-muted lp-small">
          价格由平台实时计算，结算金额以提交订单后服务端返回为准。
        </p>
      </div>

      <PageState
        v-if="!isSignedIn"
        variant="empty"
        title="请先登录"
        message="购物车与账号绑定，登录后可查看已加入的课程。"
        action-label="去登录"
        @action="navigate('/login?next=%2Fcart')"
      />
      <PageState
        v-else-if="loading"
        variant="loading"
        title="正在加载购物车…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        :title="unauthorized ? '请先登录' : '购物车加载失败'"
        :message="errorText"
        action-label="重试"
        @action="load"
      />
      <PageState
        v-else-if="items.length === 0"
        variant="empty"
        title="购物车是空的"
        message="去课程列表挑选想学的课程吧。"
        action-label="去选课"
        @action="navigate('/')"
      />

      <template v-else>
        <ul class="list lp-card">
          <li
            v-for="item in items"
            :key="item.courseId"
            class="line"
          >
            <input
              type="checkbox"
              class="check"
              :checked="isSelected(item.courseId)"
              :aria-label="`选择 ${item.title}`"
              @change="toggle(item.courseId)"
            >
            <div class="cover">
              <img
                v-if="item.coverUrl"
                :src="item.coverUrl"
                :alt="item.title"
              >
              <span
                v-else
                class="cover-fallback"
              >{{ item.title.slice(0, 2) }}</span>
            </div>
            <div class="main">
              <a
                class="course-title"
                :href="`/courses/${item.courseId}`"
              >{{ item.title }}</a>
            </div>
            <p class="lp-price price">
              ¥{{ formatYuan(item.price) }}
            </p>
            <button
              type="button"
              class="lp-btn lp-btn--ghost"
              :disabled="removing.includes(item.courseId)"
              @click="remove(item.courseId)"
            >
              {{ removing.includes(item.courseId) ? '移除中…' : '移除' }}
            </button>
          </li>
        </ul>

        <p
          v-if="notice"
          class="lp-alert notice"
        >
          {{ notice }}
        </p>

        <div class="footer lp-card lp-card-body">
          <label class="select-all">
            <input
              type="checkbox"
              class="check"
              :checked="allSelected"
              @change="toggleAll"
            >
            全选
          </label>
          <span class="lp-muted">已选 {{ selectedCount }} 门课程</span>
          <div class="footer-actions">
            <span class="lp-subtle lp-small">应付金额以服务端计算为准</span>
            <button
              type="button"
              class="lp-btn lp-btn--primary"
              :disabled="selectedCount === 0"
              @click="checkout"
            >
              去结算
            </button>
          </div>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.page-head {
  margin-bottom: 18px;
}

.title {
  margin-bottom: 4px;
  font-size: 22px;
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.line {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
}

.line + .line {
  border-top: 1px solid var(--lp-border);
}

.check {
  width: 16px;
  height: 16px;
  accent-color: var(--lp-primary);
}

.cover {
  display: flex;
  flex: 0 0 96px;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--lp-radius-sm);
  background: linear-gradient(135deg, #e8f0ff, #f5f7fa);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  color: var(--lp-primary);
  font-size: 15px;
  font-weight: 600;
}

.main {
  flex: 1 1 auto;
  min-width: 0;
}

.course-title {
  display: block;
  overflow: hidden;
  color: var(--lp-text);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  white-space: nowrap;
}

.footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
}

.select-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
</style>
