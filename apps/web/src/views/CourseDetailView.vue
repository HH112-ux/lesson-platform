<script setup lang="ts">
import { computed, ref } from 'vue'
import PageState from '../components/PageState.vue'
import {
  getCourse,
  listChapters,
  type ChapterNode,
  type CourseCard,
} from '../api/courses'
import { currentRoute, navigate } from '../router'
import { describeError, isNotFound, isUnauthorized } from '../utils/errors'
import { formatCents } from '../utils/format'
import { isSignedIn } from '../utils/session'

const courseId = Number(currentRoute.value.params.courseId)
const course = ref<CourseCard | null>(null)
const loading = ref(true)
const errorText = ref('')
const unauthorized = ref(false)
const notFound = ref(false)

const chapters = ref<ChapterNode[]>([])
const chaptersError = ref('')

const priceText = computed(() => {
  if (!course.value) {
    return ''
  }
  return course.value.price === 0 ? '免费' : `¥${formatCents(course.value.price)}`
})
const offline = computed(() => course.value?.status === 3)
const owned = computed(() => course.value?.owned === true)
const paymentEnabled = import.meta.env.VITE_PAYMENT_ENABLED === 'true'
const loginHref = computed(() => (
  `/login?next=${encodeURIComponent(`/courses/${courseId}`)}`
))

/** Loads the public course detail. */
async function loadCourse(): Promise<void> {
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  notFound.value = false
  try {
    course.value = await getCourse(courseId)
  } catch (error) {
    course.value = null
    notFound.value = isNotFound(error)
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  } finally {
    loading.value = false
  }
}

/** Loads the outline; its endpoint is not live yet, so the section reports its own failure. */
async function loadChapters(): Promise<void> {
  try {
    chapters.value = (await listChapters(courseId)).chapters ?? []
    chaptersError.value = ''
  } catch (error) {
    chapters.value = []
    chaptersError.value = describeError(error)
  }
}

function buy(): void {
  if (!course.value) {
    return
  }
  navigate(`/checkout/${course.value.courseId}`)
}

function lessonHref(lessonId: number): string {
  return `/learning/lessons/${lessonId}?courseId=${courseId}`
}

void loadCourse()
void loadChapters()
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <PageState
        v-if="loading"
        variant="loading"
        title="正在加载课程…"
      />
      <PageState
        v-else-if="notFound"
        variant="empty"
        title="课程已下架或不存在"
        message="该课程可能已下架，或你没有访问权限。"
        action-label="返回课程列表"
        @action="navigate('/courses')"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        :title="unauthorized ? '请先登录' : '课程加载失败'"
        :message="errorText"
        action-label="重试"
        @action="loadCourse"
      />

      <template v-else-if="course">
        <section class="lp-card detail">
          <div class="cover">
            <img
              v-if="course.coverUrl"
              :src="course.coverUrl"
              :alt="course.title"
            >
            <span
              v-else
              class="cover-fallback"
            >{{ course.title.slice(0, 2) }}</span>
          </div>

          <div class="info">
            <h1 class="title">
              {{ course.title }}
            </h1>
            <p class="lp-muted">
              {{ course.instructorName || '平台讲师' }} · {{ course.lessonCount }} 课时
            </p>

            <p class="lp-price price">
              {{ priceText }}
            </p>

            <p
              v-if="offline"
              class="lp-alert"
            >
              课程已下架，暂时无法购买。
            </p>

            <div class="actions">
              <button
                v-if="offline"
                type="button"
                class="lp-btn lp-btn--primary lp-btn--block"
                disabled
              >
                课程已下架
              </button>
              <a
                v-else-if="owned"
                class="lp-btn lp-btn--primary lp-btn--block"
                href="/learning/my-courses"
              >继续学习</a>
              <button
                v-else-if="!paymentEnabled"
                type="button"
                class="lp-btn lp-btn--primary lp-btn--block"
                disabled
              >
                暂未开放购买
              </button>
              <a
                v-else-if="!isSignedIn"
                class="lp-btn lp-btn--primary lp-btn--block"
                :href="loginHref"
              >登录后购买</a>
              <button
                v-else
                type="button"
                class="lp-btn lp-btn--primary lp-btn--block"
                @click="buy"
              >
                立即购买
              </button>
            </div>
          </div>
        </section>

        <section class="outline">
          <h2 class="section-title">
            课程目录
          </h2>

          <div class="lp-card">
            <PageState
              v-if="chaptersError"
              variant="error"
              title="章节目录暂不可用"
              :message="`${chaptersError}（课程共 ${course.lessonCount} 课时）`"
            />
            <PageState
              v-else-if="chapters.length === 0"
              variant="empty"
              title="暂无章节目录"
              message="课程内容正在整理中。"
            />
            <ul
              v-else
              class="chapters"
            >
              <li
                v-for="chapter in chapters"
                :key="chapter.chapterId"
                class="chapter"
              >
                <h3 class="chapter-title">
                  {{ chapter.title }}
                </h3>
                <ul class="lessons">
                  <li
                    v-for="lesson in chapter.lessons"
                    :key="lesson.lessonId"
                    class="lesson"
                  >
                    <template v-if="(owned || lesson.free) && lesson.playable !== false">
                      <a :href="lessonHref(lesson.lessonId)">{{ lesson.title }}</a>
                      <span
                        v-if="lesson.free && !owned"
                        class="lp-tag"
                      >试听</span>
                    </template>
                    <template v-else>
                      <span class="lesson-locked">{{ lesson.title }}</span>
                      <span class="lp-small lp-subtle">购买后可学习</span>
                    </template>
                  </li>
                </ul>
              </li>
            </ul>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.detail {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  padding: 24px;
}

.cover {
  display: flex;
  flex: 0 0 360px;
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
  font-size: 32px;
  font-weight: 600;
}

.info {
  display: flex;
  flex: 1 1 320px;
  flex-direction: column;
  gap: 10px;
}

.title {
  font-size: 24px;
}

.price {
  margin-top: 4px;
  font-size: 28px;
}

.actions {
  margin-top: auto;
  padding-top: 8px;
}

.outline {
  margin-top: 28px;
}

.section-title {
  margin-bottom: 12px;
  font-size: 17px;
}

.chapters {
  margin: 0;
  padding: 8px 24px 24px;
  list-style: none;
}

.chapter {
  padding-top: 16px;
}

.chapter-title {
  margin-bottom: 8px;
  font-size: 15px;
}

.lessons {
  margin: 0;
  padding: 0 0 0 4px;
  list-style: none;
}

.lesson {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  font-size: 14px;
}

.lesson-locked {
  color: var(--lp-text-muted);
}
</style>
