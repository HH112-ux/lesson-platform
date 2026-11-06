<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { listMyCourses, type MyCourseCard } from '../api/learning'
import { navigate } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'
import { formatPercent } from '../utils/format'
import { isSignedIn } from '../utils/session'

const PAGE_SIZE = 9

const courses = ref<MyCourseCard[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')
const needLogin = ref(false)

const pageCount = computed(() => (
  total.value > 0 ? Math.ceil(total.value / PAGE_SIZE) : 0
))

const loginHref = computed(() => (
  `/login?next=${encodeURIComponent('/learning/my-courses')}`
))

/** Loads one page of the personal learning list. */
async function load(): Promise<void> {
  if (!isSignedIn.value) {
    needLogin.value = true
    return
  }
  needLogin.value = false
  loading.value = true
  errorText.value = ''
  try {
    const page = await listMyCourses(pageNum.value, PAGE_SIZE)
    courses.value = page.records ?? []
    total.value = page.total ?? courses.value.length
  } catch (error) {
    courses.value = []
    total.value = 0
    errorText.value = describeError(error)
    needLogin.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/**
 * Builds the resume link of a course.
 *
 * @param course owned course
 * @returns player link when a lesson was started, detail page otherwise; a refunded course always
 *     opens its detail page because playback is denied
 * @author Caspian
 * @since 0.2.0
 */
function resumeHref(course: MyCourseCard): string {
  if (course.refunded) {
    return `/courses/${course.courseId}`
  }
  if (course.lastLessonId) {
    return `/learning/lessons/${course.lastLessonId}?courseId=${course.courseId}`
  }
  return `/courses/${course.courseId}`
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
          我的课程
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
        message="登录后可以查看已购课程与学习进度。"
        action-label="去登录"
        @action="navigate(loginHref)"
      />

      <PageState
        v-else-if="loading"
        variant="loading"
        title="正在加载学习记录…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        title="学习记录加载失败"
        :message="errorText"
        action-label="重试"
        @action="load"
      />
      <PageState
        v-else-if="courses.length === 0"
        variant="empty"
        title="还没有课程"
        message="先去挑一门想学的课程吧。"
        action-label="浏览课程"
        @action="navigate('/')"
      />

      <div
        v-else
        class="lp-grid"
      >
        <a
          v-for="course in courses"
          :key="course.courseId"
          class="course lp-card"
          :href="resumeHref(course)"
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
          </div>
          <div class="body">
            <h2 class="name">
              {{ course.title }}
              <span
                v-if="course.refunded"
                class="refunded"
              >已退款</span>
            </h2>
            <div class="progress">
              <div class="bar">
                <span
                  class="bar-fill"
                  :style="{ width: formatPercent(course.progressPercent) }"
                />
              </div>
              <span class="lp-small lp-muted">{{ formatPercent(course.progressPercent) }}</span>
            </div>
            <span class="action lp-small">
              {{ course.refunded ? '已退款，学习权限已收回' : (course.lastLessonId ? '继续学习' : '开始学习') }}
            </span>
          </div>
        </a>
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

.course {
  overflow: hidden;
  color: var(--lp-text);
}

.course:hover {
  text-decoration: none;
  box-shadow: 0 4px 12px rgba(31, 35, 41, 0.1);
}

.cover {
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

.body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px 16px;
}

.name {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar {
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef0f3;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lp-primary);
}

.action {
  color: var(--lp-primary);
}

.refunded {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f2f3f5;
  color: var(--lp-muted);
  font-size: 11px;
  font-weight: 500;
  vertical-align: middle;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 28px;
}
</style>
