<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PageState from '../components/PageState.vue'
import { getCourseProgress, getPlayToken, reportProgress } from '../api/learning'
import { currentRoute } from '../router'
import { describeError, isUnauthorized } from '../utils/errors'

const REPORT_INTERVAL_MS = 15_000

const lessonId = Number(currentRoute.value.params.lessonId)
const courseId = readCourseId(currentRoute.value.query.get('courseId'))

const video = ref<HTMLVideoElement | null>(null)
const playUrl = ref('')
const loading = ref(true)
const errorText = ref('')
const unauthorized = ref(false)
const resumeAt = ref(0)
const resumeNote = ref('')
const reportNote = ref('')
const reloading = ref(false)

let timer: number | null = null
let lastReported = -1

const courseHref = computed(() => (
  courseId === null ? null : `/courses/${courseId}`
))

function readCourseId(raw: string | null): number | null {
  if (!raw || !/^\d+$/.test(raw)) {
    return null
  }
  return Number(raw)
}

/** Loads the signed URL and the stored position of this lesson. */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  unauthorized.value = false
  try {
    const token = await getPlayToken(lessonId)
    playUrl.value = token.playUrl
    await loadResumePoint()
    loading.value = false
  } catch (error) {
    loading.value = false
    errorText.value = describeError(error)
    unauthorized.value = isUnauthorized(error)
  }
}

/** Reads {@code maxPositionSeconds} so playback continues where it stopped. */
async function loadResumePoint(): Promise<void> {
  if (courseId === null) {
    return
  }
  try {
    const progress = await getCourseProgress(courseId)
    const lesson = (progress.lessons ?? []).find((item) => item.lessonId === lessonId)
    resumeAt.value = lesson?.maxPositionSeconds ?? 0
  } catch (error) {
    resumeNote.value = `续播位置加载失败：${describeError(error)}`
  }
}

/** Seeks to the stored position once the media metadata is available. */
function applyResume(): void {
  const element = video.value
  if (!element || resumeAt.value <= 2) {
    return
  }
  const limit = Number.isFinite(element.duration) ? element.duration - 1 : resumeAt.value
  element.currentTime = Math.min(resumeAt.value, Math.max(0, limit))
}

/**
 * Sends the current position to the backend.
 *
 * @param force send even when the whole second did not change, used on pause and unload
 * @author Caspian
 * @since 0.2.0
 */
async function flush(force = false): Promise<void> {
  const element = video.value
  if (!element) {
    return
  }
  const position = Math.max(0, Math.floor(element.currentTime))
  if (!force && position === lastReported) {
    return
  }
  lastReported = position
  try {
    await reportProgress(lessonId, position)
    reportNote.value = ''
  } catch (error) {
    if (isUnauthorized(error)) {
      reportNote.value = '登录状态已失效，进度未保存'
      return
    }
    reportNote.value = `进度保存失败：${describeError(error)}`
  }
}

/** Reports while the media is actually playing. */
function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    if (video.value && !video.value.paused && !video.value.ended) {
      void flush()
    }
  }, REPORT_INTERVAL_MS)
}

function stopTimer(): void {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

/** Reports once when the tab is hidden, which also covers leaving the page. */
function onVisibilityChange(): void {
  if (document.visibilityState === 'hidden') {
    void flush(true)
  }
}

/** Reloads the signed URL once when playback fails, usually because the token expired. */
async function onVideoError(): Promise<void> {
  if (reloading.value) {
    return
  }
  reloading.value = true
  const position = video.value?.currentTime ?? 0
  try {
    const token = await getPlayToken(lessonId)
    playUrl.value = token.playUrl
    await new Promise((resolve) => window.setTimeout(resolve, 50))
    if (video.value) {
      video.value.currentTime = position
      await video.value.play().catch(() => undefined)
    }
  } catch (error) {
    reportNote.value = `播放地址刷新失败：${describeError(error)}`
  } finally {
    reloading.value = false
  }
}

onMounted(() => {
  void load()
  startTimer()
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('pagehide', onPageHide)
})

onBeforeUnmount(() => {
  void flush(true)
  stopTimer()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('pagehide', onPageHide)
})

function onPageHide(): void {
  void flush(true)
}
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <PageState
        v-if="loading"
        variant="loading"
        title="正在获取播放地址…"
      />
      <PageState
        v-else-if="errorText"
        variant="error"
        :title="unauthorized ? '请先登录' : '无法播放该课时'"
        :message="errorText"
        action-label="重试"
        @action="load"
      />

      <template v-else>
        <section class="player lp-card">
          <video
            ref="video"
            class="video"
            :src="playUrl"
            controls
            playsinline
            preload="metadata"
            @loadedmetadata="applyResume"
            @pause="flush(true)"
            @ended="flush(true)"
            @error="onVideoError"
          />
        </section>

        <section class="meta">
          <div>
            <h1 class="title">
              课时播放
            </h1>
            <p class="lp-muted lp-small">
              播放位置每 15 秒自动保存，暂停或离开时会补报一次。
            </p>
          </div>
          <a
            v-if="courseHref"
            class="lp-btn"
            :href="courseHref"
          >返回课程</a>
        </section>

        <p
          v-if="resumeNote"
          class="lp-alert"
        >
          {{ resumeNote }}
        </p>
        <p
          v-if="reportNote"
          class="lp-alert"
        >
          {{ reportNote }}
        </p>
      </template>
    </div>
  </main>
</template>

<style scoped>
.player {
  overflow: hidden;
  background: #000000;
}

.video {
  display: block;
  width: 100%;
  max-height: 68vh;
  background: #000000;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
}

.title {
  margin-bottom: 4px;
  font-size: 20px;
}

.lp-alert {
  margin-top: 16px;
}
</style>
