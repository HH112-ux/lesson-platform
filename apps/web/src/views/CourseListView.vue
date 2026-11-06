<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { listCategoryTree, listCourses, type CategoryNode, type CourseCard } from '../api/courses'
import { listMyCourses } from '../api/learning'
import { currentRoute } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'
import { isSignedIn } from '../utils/session'

const PAGE_SIZE = 12

const query = currentRoute.value.query
const keyword = ref(query.get('keyword') ?? '')
const appliedKeyword = ref(keyword.value.trim())
const categoryId = ref<number | null>(readCategory(query.get('categoryId')))
const pageNum = ref(readPage(query.get('pageNum')))

const courses = ref<CourseCard[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const unauthorized = ref(false)

const categories = ref<CategoryNode[]>([])
const categoryError = ref('')

const ownedIds = ref(new Set<number>())
const ownedError = ref('')

const pageCount = computed(() => (
  total.value > 0 ? Math.ceil(total.value / PAGE_SIZE) : 0
))

const totalLabel = computed(() => (total.value > 0 ? `共 ${total.value} 门课程` : ''))

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
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    courses.value = page.records ?? []
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
    categories.value = await listCategoryTree()
    categoryError.value = ''
  } catch (error) {
    categories.value = []
    categoryError.value = `分类加载失败：${describeError(error)}`
  }
}

/** Loads owned course ids so the cards can carry the purchase badge. */
async function loadOwned(): Promise<void> {
  if (!isSignedIn.value) {
    ownedIds.value = new Set()
    ownedError.value = ''
    return
  }
  try {
    const page = await listMyCourses(1, 100)
    ownedIds.value = new Set((page.records ?? []).map((item) => item.courseId))
    ownedError.value = ''
  } catch (error) {
    ownedError.value = `已购标记加载失败：${describeError(error)}`
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

void Promise.all([load(), loadCategories(), loadOwned()])
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
            {{ totalLabel || '按分类或关键词找到想学的课程' }}
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
          :key="item.id"
          type="button"
          class="chip"
          :class="{ active: categoryId === item.id }"
          @click="selectCategory(item.id)"
        >
          {{ item.name }}
        </button>
      </div>
      <p
        v-else-if="categoryError"
        class="lp-small lp-subtle"
      >
        {{ categoryError }}
      </p>

      <p
        v-if="ownedError"
        class="lp-small lp-subtle owned-hint"
      >
        {{ ownedError }}
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
        <a
          v-for="course in courses"
          :key="course.courseId"
          class="course lp-card"
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
              v-if="ownedIds.has(course.courseId)"
              class="owned"
            >已购</span>
          </div>
          <div class="body">
            <h2 class="name">
              {{ course.title }}
            </h2>
            <p class="lp-muted lp-small">
              {{ course.lessonCount ? `${course.lessonCount} 课时` : '课程详情见内页' }}
            </p>
            <p class="lp-price price">
              {{ course.price === 0 ? '免费' : `¥${formatCents(course.price)}` }}
            </p>
          </div>
        </a>
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

.owned-hint {
  margin-bottom: 12px;
}

.course {
  overflow: hidden;
  color: var(--lp-text);
}

.course:hover {
  text-decoration: none;
  box-shadow: 0 4px 12px rgba(31, 35, 41, 0.1);
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
  padding: 14px 16px 16px;
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
