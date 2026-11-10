<script setup lang="ts">
/**
 * Lesson video screen of the operations console.
 *
 * <p>Menu visibility is role based convenience only; the backend still checks course ownership on
 * every call, so this screen is not an isolation boundary.</p>
 *
 * @author Caspian
 * @since 0.9.0
 */
import { nextTick, onMounted, ref, shallowRef } from 'vue'
import {
  confirmLessonUpload,
  createLessonUploadSession,
  getLessonPlayUrl,
  listTeacherChapters,
  listTeacherCourses,
  type TeacherChapter,
  type TeacherCourse,
  type TeacherLesson,
  type TeacherUploadSession,
} from '../api/teacher'
import { ApiError } from '../api/client'
import { describeError } from '../utils/errors'
import { checkLocalVideo, formatBytes, isSlowUpload } from '../utils/lessonUpload'

const PAGE_SIZE = 50

const STATUS_TEXT: Record<number, string> = { 0: '草稿', 1: '待审核', 2: '已上架', 3: '已下架' }

const courses = ref<TeacherCourse[]>([])
const courseId = ref<number | null>(null)
const chapters = ref<TeacherChapter[]>([])
const loading = ref(false)
const errorText = ref('')
const notice = ref('')

const target = shallowRef<{ courseId: number; chapterId: number; lesson: TeacherLesson } | null>(
  null,
)
const fileInput = ref<HTMLInputElement | null>(null)
const session = shallowRef<TeacherUploadSession | null>(null)
const file = shallowRef<File | null>(null)
const busy = ref(false)
const phase = ref('')
const progress = ref(0)
const failure = ref('')

/** Lesson currently open in the preview player, keeping the identifiers needed to re-sign. */
const preview = shallowRef<{
  courseId: number
  chapterId: number
  lessonId: number
  title: string
  playUrl: string
} | null>(null)
const previewVideo = ref<HTMLVideoElement | null>(null)
const previewReloading = ref(false)

async function loadCourses(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await listTeacherCourses(undefined, 1, PAGE_SIZE)
    courses.value = page.items
  } catch (error) {
    courses.value = []
    errorText.value = describeTeacherError(error, '课程列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadChapters(): Promise<void> {
  const id = courseId.value
  chapters.value = []
  if (id === null) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    chapters.value = await listTeacherChapters(id)
  } catch (error) {
    errorText.value = describeTeacherError(error, '课时列表加载失败')
  } finally {
    loading.value = false
  }
}

function selectCourse(): void {
  notice.value = ''
  failure.value = ''
  void loadChapters()
}

function pickVideo(chapterId: number, lesson: TeacherLesson): void {
  if (courseId.value === null || busy.value) {
    return
  }
  target.value = { courseId: courseId.value, chapterId, lesson }
  failure.value = ''
  notice.value = ''
  fileInput.value?.click()
}

/**
 * Asks the server for a fresh signed playback URL and opens it; the list only carries {@code hasVideo}.
 */
async function previewLesson(chapterId: number, lesson: TeacherLesson): Promise<void> {
  const id = courseId.value
  if (id === null) {
    return
  }
  failure.value = ''
  notice.value = ''
  try {
    const credential = await getLessonPlayUrl(id, chapterId, lesson.lessonId)
    preview.value = {
      courseId: id,
      chapterId,
      lessonId: lesson.lessonId,
      title: lesson.title,
      playUrl: credential.playUrl,
    }
  } catch (error) {
    failure.value = describeTeacherError(error, '预览地址获取失败，请稍后重试')
  }
}

/**
 * Re-signs the preview URL once per media error and keeps the playback position.
 *
 * <p>The signed URL lives ten minutes, which is shorter than long lessons; without this the player
 * would stop silently when the credential expires.</p>
 */
async function onPreviewError(): Promise<void> {
  const current = preview.value
  if (!current || previewReloading.value) {
    return
  }
  previewReloading.value = true
  const position = previewVideo.value?.currentTime ?? 0
  try {
    const credential = await getLessonPlayUrl(
      current.courseId,
      current.chapterId,
      current.lessonId,
    )
    if (preview.value !== current) {
      return
    }
    preview.value = { ...current, playUrl: credential.playUrl }
    await nextTick()
    const video = previewVideo.value
    if (video) {
      await restorePosition(video, position)
      await video.play().catch(() => undefined)
    }
  } catch (error) {
    failure.value = describeTeacherError(error, '预览地址刷新失败，请稍后重试')
  } finally {
    previewReloading.value = false
  }
}

/**
 * Restores the playback position once the re-signed source has metadata.
 *
 * <p>Assigning {@code currentTime} before {@code loadedmetadata} is silently dropped, so a fixed
 * delay cannot guarantee it survives; the timeout only keeps an unreadable source from hanging.</p>
 */
function restorePosition(video: HTMLVideoElement, position: number): Promise<void> {
  if (position <= 0) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    const apply = (): void => {
      window.clearTimeout(timer)
      video.removeEventListener('loadedmetadata', apply)
      video.currentTime = position
      resolve()
    }
    const timer = window.setTimeout(apply, 2000)
    video.addEventListener('loadedmetadata', apply)
    if (video.readyState >= 1) {
      apply()
    }
  })
}

function closePreview(): void {
  preview.value = null
}

function onFilePicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = input.files?.[0] ?? null
  input.value = ''
  if (!picked) {
    return
  }
  if (!target.value) {
    return
  }
  file.value = picked
  void openSessionAndUpload()
}

/**
 * Creates a fresh session for the picked file, then uploads and confirms it.
 *
 * <p>A session is never reused once it failed with {@code 61007} or {@code 61008}; the next
 * attempt always starts from a new credential.</p>
 */
async function openSessionAndUpload(): Promise<void> {
  const current = target.value
  const picked = file.value
  if (!current || !picked) {
    return
  }
  failure.value = ''
  notice.value = ''
  const duration = await readDuration(picked)
  const reason = checkLocalVideo(picked, duration)
  if (reason) {
    failure.value = reason
    return
  }
  if (isSlowUpload(picked.size)) {
    notice.value = '文件超过 500 MiB：上传较慢，且不支持断点续传，请保持页面打开。'
  }
  busy.value = true
  progress.value = 0
  phase.value = '创建上传会话'
  try {
    const created = await createLessonUploadSession(
      current.courseId,
      current.chapterId,
      current.lesson.lessonId,
      picked.size,
    )
    if (created.expectedSizeBytes !== picked.size) {
      failure.value = `服务端会话期望 ${created.expectedSizeBytes} 字节，所选文件为 ${picked.size} 字节，请重新选择文件`
      return
    }
    session.value = created
    await putAndConfirm()
  } catch (error) {
    fail(error)
  } finally {
    busy.value = false
  }
}

/** Uploads the bytes of the held session and confirms it; retries reuse the same session. */
async function putAndConfirm(): Promise<void> {
  const current = target.value
  const picked = file.value
  const held = session.value
  if (!current || !picked || !held) {
    return
  }
  busy.value = true
  failure.value = ''
  try {
    phase.value = '上传中'
    await putObject(held, picked)
    phase.value = '确认中'
    const lesson = await confirmLessonUpload(
      current.courseId,
      current.chapterId,
      current.lesson.lessonId,
      held.uploadId,
    )
    notice.value = `《${lesson.title}》已确认：${lesson.videoCodec.toUpperCase()} ${lesson.videoWidth}×${lesson.videoHeight}，${formatBytes(lesson.videoSizeBytes)}`
    session.value = null
    file.value = null
    target.value = null
    phase.value = ''
    await loadChapters()
  } catch (error) {
    fail(error)
  } finally {
    busy.value = false
  }
}

function fail(error: unknown): void {
  const code = error instanceof ApiError ? error.code : 0
  if (code === 61007 || code === 61008) {
    session.value = null
    file.value = null
    target.value = null
  }
  phase.value = ''
  failure.value = describeTeacherError(error, '上传失败，请重试')
}

/**
 * Maps a teacher endpoint failure to user facing text.
 *
 * <p>{@code 40301} here means the caller holds the teacher role but has no teacher profile yet, so
 * the message points at onboarding instead of a generic permission error.</p>
 */
function describeTeacherError(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.code === 40301) {
    return '当前账号尚未完成教师入驻，请完成入驻审核后再上传'
  }
  return describeError(error, fallback)
}

/** Reads the duration locally so a wrong file fails before a session is created. */
function readDuration(picked: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(picked)
    const video = document.createElement('video')
    const finish = (value: number | null): void => {
      URL.revokeObjectURL(url)
      resolve(value)
    }
    video.preload = 'metadata'
    video.onloadedmetadata = () =>
      finish(Number.isFinite(video.duration) && video.duration > 0 ? video.duration : null)
    video.onerror = () => finish(null)
    video.src = url
  })
}

/** Single presigned PUT, no multipart and no resume; progress comes from XHR. */
function putObject(held: TeacherUploadSession, picked: File): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', held.uploadUrl)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        progress.value = Math.round((event.loaded / event.total) * 100)
      }
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        progress.value = 100
        resolve()
      } else {
        reject(new Error(`视频上传失败（HTTP ${xhr.status}）`))
      }
    }
    xhr.onerror = () => reject(new Error('视频上传中断，请检查网络后重试'))
    xhr.send(picked)
  })
}

onMounted(loadCourses)
</script>

<template>
  <section>
    <header class="page-head">
      <h1>课时视频</h1>
      <div class="search">
        <select
          v-model.number="courseId"
          class="lp-input"
          :disabled="busy"
          @change="selectCourse"
        >
          <option :value="null">
            请选择课程
          </option>
          <option
            v-for="course in courses"
            :key="course.courseId"
            :value="course.courseId"
          >
            {{ course.title }}（{{ STATUS_TEXT[course.status] ?? '未知' }}）
          </option>
        </select>
        <button
          class="lp-btn"
          :disabled="busy || courseId === null"
          @click="loadChapters"
        >
          刷新
        </button>
      </div>
    </header>

    <p
      v-if="notice || failure"
      :class="failure ? 'lp-error' : 'lp-notice'"
    >
      {{ failure || notice }}
      <button
        v-if="session && !busy"
        class="lp-btn"
        @click="putAndConfirm"
      >
        重试上传
      </button>
    </p>

    <div
      v-if="busy"
      class="lp-card progress-card"
    >
      <span>{{ phase }}：{{ target?.lesson.title }}（{{ progress }}%）</span>
      <progress
        :value="progress"
        max="100"
      />
    </div>

    <div class="lp-card table-card">
      <p
        v-if="loading"
        class="lp-empty"
      >
        加载中…
      </p>
      <p
        v-else-if="errorText"
        class="lp-empty"
      >
        {{ errorText }}
      </p>
      <p
        v-else-if="chapters.length === 0"
        class="lp-empty"
      >
        请选择课程查看课时
      </p>
      <div
        v-for="chapter in chapters"
        v-else
        :key="chapter.chapterId"
        class="chapter"
      >
        <h2>{{ chapter.sort }}. {{ chapter.title }}</h2>
        <table class="lp-table">
          <thead>
            <tr>
              <th>课时</th>
              <th>时长</th>
              <th>编码</th>
              <th>分辨率</th>
              <th>大小</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="lesson in chapter.lessons"
              :key="lesson.lessonId"
            >
              <td>{{ lesson.title }}</td>
              <td>{{ lesson.durationSec > 0 ? `${lesson.durationSec} 秒` : '--' }}</td>
              <td>{{ lesson.videoCodec ? lesson.videoCodec.toUpperCase() : '--' }}</td>
              <td>
                {{ lesson.videoWidth > 0 ? `${lesson.videoWidth}×${lesson.videoHeight}` : '--' }}
              </td>
              <td>{{ lesson.videoSizeBytes > 0 ? formatBytes(lesson.videoSizeBytes) : '--' }}</td>
              <td>{{ lesson.hasVideo ? '已上传' : '未上传' }}</td>
              <td>
                <button
                  v-if="lesson.hasVideo"
                  class="lp-btn"
                  :disabled="busy"
                  @click="previewLesson(chapter.chapterId, lesson)"
                >
                  预览
                </button>
                <button
                  class="lp-btn"
                  :disabled="busy"
                  @click="pickVideo(chapter.chapterId, lesson)"
                >
                  {{ lesson.hasVideo ? '替换视频' : '上传视频' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      accept="video/mp4"
      hidden
      @change="onFilePicked"
    >

    <div
      v-if="preview"
      class="preview-mask"
      @click.self="closePreview"
    >
      <div class="preview-card">
        <header>
          <h2>{{ preview.title }}</h2>
          <button
            class="lp-btn"
            @click="closePreview"
          >
            关闭
          </button>
        </header>
        <video
          ref="previewVideo"
          :src="preview.playUrl"
          controls
          autoplay
          @error="onPreviewError"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
h1 { margin: 0; font-size: 20px; }
.search { display: flex; gap: 8px; }
.search .lp-input { width: 280px; }
.table-card { padding: 0 16px 16px; overflow: hidden; }
.chapter h2 { margin: 16px 0 8px; font-size: 15px; }
.progress-card { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.progress-card progress { flex: 1; }
.preview-mask { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.55); z-index: 20; }
.preview-card { width: min(880px, 92vw); padding: 16px; border-radius: 8px; background: #fff; }
.preview-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.preview-card h2 { margin: 0; font-size: 16px; }
.preview-card video { width: 100%; max-height: 70vh; margin-top: 12px; background: #000; }
</style>
