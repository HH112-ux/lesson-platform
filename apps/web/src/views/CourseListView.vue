<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { listCategories, listCourses, type Category, type CourseCard } from '../api/courses'
import { addCartItem, getCart } from '../api/cart'
import { ApiError } from '../api/client'
import { currentRoute, navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'
import { isSignedIn } from '../utils/session'

const PAGE_SIZE = 12

const query = currentRoute.value.query
const keyword = ref(query.get('keyword') ?? '')
const appliedKeyword = ref(keyword.value.trim())
const categoryId = ref<number | null>(readCategory(query.get('category')))
const pageNum = ref(readPage(query.get('page')))

const courses = ref<CourseCard[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const unauthorized = ref(false)

const categories = ref<Category[]>([])
const categoryError = ref('')

const addedIds = ref<number[]>([])
const addingId = ref<number | null>(null)
const noticeById = ref<Record<number, string>>({})

const loginHref = computed(() => {
  const next = typeof window === 'undefined'
    ? '/'
    : `${window.location.pathname}${window.location.search}`
  return `/login?next=${encodeURIComponent(next)}`
})

const pageCount = computed(() => (
  total.value > 0 ? Math.ceil(total.value / PAGE_SIZE) : 0
))

const summaryLabel = computed(() => (
  total.value > 0 ? `共 ${total.value} 门课程` : '按分类或关键词找到想学的课程'
))

function readCategory(raw: string | null): number | null {
  if (!raw || !/^\d+$/.test(raw)) {
    return null
  }
  return Number(raw)
}

function readPage(raw: string | null): number {
  if (!raw || !/^\d+$/.test(raw)) {
    return 1
  }
  return Math.max(1, Number(raw))
}

/** Loads one page of the catalog with the applied filters. */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  try {
    const page = await listCourses({
      keyword: appliedKeyword.value || undefined,
      categoryId: categoryId.value,
      page: pageNum.value,
      size: PAGE_SIZE,
    })
    courses.value = page.items ?? []
    total.value = page.total ?? courses.value.length
  } catch (error) {
    courses.value = []
    total.value = 0
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/** Loads the category chips; a failure only hides the filter row. */
async function loadCategories(): Promise<void> {
  try {
    categories.value = await listCategories()
    categoryError.value = ''
  } catch (error) {
    categories.value = []
    categoryError.value = `分类加载失败：${describeError(error)}`
  }
}

function search(): void {
  appliedKeyword.value = keyword.value.trim()
  pageNum.value = 1
  void load()
}

function selectCategory(id: number | null): void {
  if (categoryId.value === id) {
    return
  }
  categoryId.value = id
  pageNum.value = 1
  void load()
}

function goPage(next: number): void {
  if (next < 1 || (pageCount.value > 0 && next > pageCount.value)) {
    return
  }
  pageNum.value = next
  void load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function retry(): void {
  void load()
}

/** Tells whether the course already sits in the cart of this visitor. */
function isAdded(course: CourseCard): boolean {
  return addedIds.value.includes(course.courseId)
}

function setNotice(courseId: number, text: string): void {
  noticeById.value = { ...noticeById.value, [courseId]: text }
}

/** Leaves for the sign-in page and returns to the current catalog URL afterwards. */
function goLogin(): void {
  navigate(loginHref.value)
}

/** Opens the course detail, where the purchase action lives; anonymous visitors sign in first. */
function openDetail(course: CourseCard): void {
  if (!isSignedIn.value) {
    goLogin()
    return
  }
  navigate(`/courses/${course.courseId}`)
}

/** Loads the server side cart once so already added courses show the cart link. */
async function syncCart(): Promise<void> {
  if (!isSignedIn.value) {
    return
  }
  try {
    const cart = await getCart()
    addedIds.value = (cart.items ?? []).map((item) => item.courseId)
  } catch {
    addedIds.value = []
  }
}

/** Adds one course without leaving the catalog; repeats are treated as already added. */
async function addToCart(course: CourseCard): Promise<void> {
  if (isAdded(course) || addingId.value === course.courseId) {
    return
  }
  if (!isSignedIn.value) {
    goLogin()
    return
  }
  addingId.value = course.courseId
  setNotice(course.courseId, '')
  try {
    await addCartItem(course.courseId)
    addedIds.value = [...addedIds.value, course.courseId]
  } catch (error) {
    if (isUnauthorized(error)) {
      goLogin()
      return
    }
    if (error instanceof ApiError && error.code === 61002) {
      setNotice(course.courseId, '你已拥有该课程，无需加入购物车。')
      return
    }
    setNotice(course.courseId, describeError(error))
  } finally {
    addingId.value = null
  }
}

void Promise.all([load(), loadCategories(), syncCart()])
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <section class="hero">
        <div>
          <h1 class="title">
            全部课程
          </h1>
          <p class="lp-muted lp-small">
            {{ summaryLabel }}
          </p>
        </div>

        <form
          class="search"
          @submit.prevent="search"
        >
          <input
            v-model="keyword"
            class="lp-input"
            placeholder="搜索课程名称"
            aria-label="搜索课程"
          >
          <button
            type="submit"
            class="lp-btn lp-btn--primary"
          >
            搜索
          </button>
        </form>
      </section>

      <p
        v-if="!isSignedIn"
        class="notice"
      >
        登录后可以购买课程并同步学习进度。
        <a href="/login?next=/">去登录</a>
      </p>

      <div
        v-if="categories.length"
        class="chips"
      >
        <button
          type="button"
          class="chip"
          :class="{ active: categoryId === null }"
          @click="selectCategory(null)"
        >
          全部
        </button>
        <button
          v-for="item in categories"
          :key="item.categoryId"
          type="button"
          class="chip"
          :class="{ active: categoryId === item.categoryId }"
          @click="selectCategory(item.categoryId)"
        >
          {{ item.name }}
        </button>
      </div>
      <p
        v-else-if="categoryError"
        class="lp-small lp-subtle category-error"
      >
        {{ categoryError }}
      </p>

      <PageState
        v-if="loading"
        variant="loading"
        title="正在加载课程…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        :title="unauthorized ? '请先登录' : '课程加载失败'"
        :message="errorText"
        action-label="重试"
        @action="retry"
      />
      <PageState
        v-else-if="courses.length === 0"
        variant="empty"
        title="暂无课程"
        message="换个关键词或分类再试试。"
      />

      <div
        v-else
        class="lp-grid"
      >
        <article
          v-for="course in courses"
          :key="course.courseId"
          class="course lp-card"
        >
          <a
            class="card-main"
            :href="`/courses/${course.courseId}`"
          >
            <div class="cover">
              <img
                v-if="course.coverUrl"
                :src="course.coverUrl"
                :alt="course.title"
                loading="lazy"
              >
              <span
                v-else
                class="cover-fallback"
              >{{ course.title.slice(0, 2) }}</span>
              <span
                v-if="course.owned"
                class="owned"
              >已购</span>
            </div>
            <div class="body">
              <h2 class="name">
                {{ course.title }}
              </h2>
              <p class="lp-muted lp-small">
                {{ course.instructorName || '课程' }} · {{ course.lessonCount }} 课时
              </p>
            </div>
          </a>
          <div class="body foot">
            <p class="lp-price price">
              {{ course.price === 0 ? '免费' : `¥${formatCents(course.price)}` }}
            </p>
            <div class="card-actions">
              <a
                v-if="course.owned"
                class="lp-btn lp-btn--ghost card-action"
                href="/learning/my-courses"
                @click.stop
              >继续学习</a>
              <template v-else-if="course.price === 0">
                <button
                  type="button"
                  class="lp-btn lp-btn--primary card-action"
                  @click.stop.prevent="openDetail(course)"
                >
                  免费学习
                </button>
              </template>
              <template v-else>
                <a
                  v-if="isAdded(course)"
                  class="lp-btn lp-btn--ghost card-action"
                  href="/cart"
                  @click.stop
                >已加入 · 去购物车</a>
                <button
                  v-else
                  type="button"
                  class="lp-btn lp-btn--ghost card-action"
                  :disabled="addingId === course.courseId"
                  @click.stop.prevent="addToCart(course)"
                >
                  {{ addingId === course.courseId ? '加入中…' : '加入购物车' }}
                </button>
                <button
                  type="button"
                  class="lp-btn lp-btn--primary card-action"
                  @click.stop.prevent="openDetail(course)"
                >
                  购买
                </button>
              </template>
            </div>
          </div>
          <p
            v-if="noticeById[course.courseId]"
            class="lp-alert card-notice"
            aria-live="polite"
          >
            {{ noticeById[course.courseId] }}
          </p>
        </article>
      </div>

      <div
        v-if="!loading && !errorText && pageCount > 1"
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
.hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.title {
  font-size: 24px;
}

.search {
  display: flex;
  gap: 8px;
}

.search .lp-input {
  width: 240px;
}

.notice {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: var(--lp-radius-sm);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
  font-size: 13px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.chip {
  padding: 5px 14px;
  border: 1px solid var(--lp-border);
  border-radius: 999px;
  background: var(--lp-surface);
  color: var(--lp-text-muted);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.chip.active {
  border-color: var(--lp-primary);
  background: var(--lp-primary-soft);
  color: var(--lp-primary);
}

.category-error {
  margin-bottom: 16px;
}

.course {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--lp-text);
}

.course:hover {
  text-decoration: none;
  box-shadow: 0 4px 12px rgba(31, 35, 41, 0.1);
}

.card-main {
  display: block;
  color: inherit;
}

.card-main:hover {
  text-decoration: none;
}

.cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, #e8f0ff, #f5f7fa);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  color: var(--lp-primary);
  font-size: 24px;
  font-weight: 600;
}

.owned {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(31, 122, 77, 0.9);
  color: #ffffff;
  font-size: 12px;
  line-height: 20px;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px 8px;
}

.foot {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 16px 16px;
}

.foot .price {
  margin-top: 0;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.card-action {
  padding: 5px 12px;
  font-size: 13px;
  white-space: nowrap;
}

.card-action:disabled {
  cursor: wait;
  opacity: 0.65;
}

.card-notice {
  margin: 0 16px 14px;
  font-size: 12px;
}

.name {
  display: -webkit-box;
  overflow: hidden;
  font-size: 15px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.price {
  margin-top: 4px;
  font-size: 16px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 28px;
}
</style>
