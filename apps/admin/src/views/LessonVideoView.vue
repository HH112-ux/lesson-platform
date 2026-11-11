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
import { computed, nextTick, onMounted, reactive, ref, shallowRef } from 'vue'
import {
  confirmLessonUpload,
  createLessonUploadSession,
  createTeacherChapter,
  createTeacherLesson,
  deleteTeacherLesson,
  getLessonPlayUrl,
  getTeacherCourse,
  listTeacherChapters,
  listTeacherCourses,
  updateTeacherLesson,
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

/** Sales state of the selected course; {@code null} means unknown and keeps deletion hidden. */
const courseSold = ref<boolean | null>(null)

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

/** Transcode slot; the backend field is not live yet, so the column renders an unknown value. */
const TRANSCODE_TEXT: Record<string, string> = {
  PENDING: '转码中',
  RUNNING: '转码中',
  READY: '已就绪',
  FAILED: '转码失败',
}

const chapterFormOpen = ref(false)
const lessonFormOpen = ref(false)
const saving = ref(false)
const chapterForm = reactive({ title: '' })
const lessonForm = reactive({
  chapterId: 0,
  chapterTitle: '',
  lessonId: null as number | null,
  title: '',
  sort: 0 as number | '',
  durationSec: 0,
})

/** Explains why the delete action is missing, instead of leaving the teacher guessing. */
const lessonDeletionNote = computed(() => {
  if (courseSold.value === true) {
    return '该课程已有成功支付订单：为保证已购学员的播放与学习进度，课时不可删除。'
  }
  return courseSold.value === null && courseId.value !== null
    ? '暂时无法确认课程售出状态，课时删除入口已隐藏。'
    : ''
})

/** Renders the transcode state when it arrives; unknown until the field is returned. */
function transcodeText(lesson: TeacherLesson): string {
  const state = lesson.transcodeStatus
  if (!state) {
    return '--'
  }
  return TRANSCODE_TEXT[state] ?? state
}

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
  void loadCourseSales()
}

/** Loads the sales state used to hide lesson deletion; an unknown answer stays hidden. */
async function loadCourseSales(): Promise<void> {
  const id = courseId.value
  courseSold.value = null
  if (id !== null) {
    try {
      courseSold.value = (await getTeacherCourse(id)).sold
    } catch {
      courseSold.value = null
    }
  }
}

function selectCourse(): void {
  notice.value = ''
  failure.value = ''
  chapterFormOpen.value = false
  lessonFormOpen.value = false
  void loadChapters()
}

function openChapterForm(): void {
  chapterForm.title = ''
  notice.value = ''
  failure.value = ''
  lessonFormOpen.value = false
  chapterFormOpen.value = true
}

/** Creates a chapter; the backend defaults visibility and sort, and later chapters follow in order. */
async function saveChapter(): Promise<void> {
  const id = courseId.value
  const title = chapterForm.title.trim()
  if (id === null || !title) {
    failure.value = '章节标题不能为空'
    return
  }
  saving.value = true
  failure.value = ''
  notice.value = ''
  try {
    await createTeacherChapter(id, { title })
    chapterFormOpen.value = false
    notice.value = `章节「${title}」已创建，接着添加课时`
    await loadChapters()
  } catch (error) {
    failure.value = describeContentError(error, '章节创建失败')
  } finally {
    saving.value = false
  }
}

function openLessonForm(chapter: TeacherChapter): void {
  lessonForm.chapterId = chapter.chapterId
  lessonForm.chapterTitle = chapter.title
  lessonForm.lessonId = null
  lessonForm.title = ''
  lessonForm.sort = chapter.lessons.reduce((max, lesson) => Math.max(max, lesson.sort), -1) + 1
  lessonForm.durationSec = 0
  notice.value = ''
  failure.value = ''
  chapterFormOpen.value = false
  lessonFormOpen.value = true
}

function openLessonEdit(chapter: TeacherChapter, lesson: TeacherLesson): void {
  lessonForm.chapterId = chapter.chapterId
  lessonForm.chapterTitle = chapter.title
  lessonForm.lessonId = lesson.lessonId
  lessonForm.title = lesson.title
  lessonForm.sort = lesson.sort
  lessonForm.durationSec = lesson.durationSec
  notice.value = ''
  failure.value = ''
  chapterFormOpen.value = false
  lessonFormOpen.value = true
}

/** Creates or edits a lesson; duration stays owned by the upload confirmation call. */
async function saveLesson(): Promise<void> {
  const id = courseId.value
  const title = lessonForm.title.trim()
  const sort = Number(lessonForm.sort)
  if (id === null || !title) {
    failure.value = '课时标题不能为空'
    return
  }
  if (lessonForm.sort === '' || !Number.isInteger(sort) || sort < 0) {
    failure.value = '排序需为非负整数'
    return
  }
  saving.value = true
  failure.value = ''
  notice.value = ''
  try {
    if (lessonForm.lessonId === null) {
      await createTeacherLesson(id, lessonForm.chapterId, { title, sort })
      notice.value = `课时「${title}」已创建，上传并确认视频后即可提交审核`
    } else {
      await updateTeacherLesson(id, lessonForm.chapterId, lessonForm.lessonId, { title, sort })
      notice.value = `课时「${title}」已更新`
    }
    lessonFormOpen.value = false
    await loadChapters()
  } catch (error) {
    failure.value = describeContentError(error, '课时保存失败')
  } finally {
    saving.value = false
  }
}

/** Deletes a lesson after an explicit confirmation; sold courses never expose this action. */
async function removeLesson(chapter: TeacherChapter, lesson: TeacherLesson): Promise<void> {
  const id = courseId.value
  if (id === null || busy.value || saving.value) {
    return
  }
  if (!window.confirm(`确认删除课时「${lesson.title}」？删除后不可恢复。`)) {
    return
  }
  saving.value = true
  failure.value = ''
  notice.value = ''
  try {
    await deleteTeacherLesson(id, chapter.chapterId, lesson.lessonId)
    notice.value = `课时「${lesson.title}」已删除`
    await loadChapters()
  } catch (error) {
    failure.value = describeContentError(error, '课时删除失败')
  } finally {
    saving.value = false
  }
}

/** Maps chapter and lesson failures to the rule that blocked the save. */
function describeContentError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.code === 40001) {
      return '填写内容不合法：标题不超过 120 字，排序需为非负整数'
    }
    if (error.code === 40901) {
      return '课程当前状态不可修改：仅草稿或已下架课程可维护章节与课时'
    }
    if (error.code === 40301) {
      return '只有课程所属教师本人可以维护章节与课时'
    }
    if (error.code === 61009) {
      return '该课程已有成功支付订单，课时不能删除；如需下架内容请联系运营'
    }
    if (error.code === 50401) {
      return '暂时无法确认课程售出状态，课时删除已停止，请稍后重试'
    }
  }
  return describeError(error, fallback)
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

onMounted(async () => {
  await loadCourses()
  const raw = new URLSearchParams(window.location.search).get('courseId')
  const requested = raw === null ? Number.NaN : Number(raw)
  if (Number.isInteger(requested) && courses.value.some((course) => course.courseId === requested)) {
    courseId.value = requested
    await loadChapters()
  }
})
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
        <button
          class="lp-btn lp-btn--primary"
          :disabled="busy || saving || courseId === null || chapterFormOpen"
          @click="openChapterForm"
        >
          新增章节
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

    <form
      v-if="chapterFormOpen"
      class="lp-card form-card"
      @submit.prevent="saveChapter"
    >
      <h2>新增章节</h2>
      <label class="lp-field">
        <span class="lp-label">章节标题</span>
        <input
          v-model="chapterForm.title"
          class="lp-input"
          maxlength="120"
          placeholder="例如：第一章 基础入门"
        >
      </label>
      <div class="actions">
        <button
          class="lp-btn lp-btn--primary"
          type="submit"
          :disabled="saving"
        >
          {{ saving ? '保存中…' : '保存章节' }}
        </button>
        <button
          class="lp-btn"
          type="button"
          :disabled="saving"
          @click="chapterFormOpen = false"
        >
          取消
        </button>
      </div>
    </form>

    <form
      v-if="lessonFormOpen"
      class="lp-card form-card"
      @submit.prevent="saveLesson"
    >
      <h2>{{ lessonForm.lessonId === null ? '新增课时' : '编辑课时' }}（{{ lessonForm.chapterTitle }}）</h2>
      <label class="lp-field">
        <span class="lp-label">课时标题</span>
        <input
          v-model="lessonForm.title"
          class="lp-input"
          maxlength="120"
          placeholder="例如：1.1 环境准备"
        >
      </label>
      <label class="lp-field">
        <span class="lp-label">排序（非负整数，越小越靠前）</span>
        <input
          v-model.number="lessonForm.sort"
          class="lp-input"
          type="number"
          min="0"
          step="1"
        >
      </label>
      <p
        v-if="lessonForm.lessonId !== null"
        class="lp-muted form-note"
      >
        时长：{{ lessonForm.durationSec > 0 ? `${lessonForm.durationSec} 秒（由视频确认写入）` : '暂无视频，上传确认后自动写入' }}
      </p>
      <div class="actions">
        <button
          class="lp-btn lp-btn--primary"
          type="submit"
          :disabled="saving"
        >
          {{ saving ? '保存中…' : '保存课时' }}
        </button>
        <button
          class="lp-btn"
          type="button"
          :disabled="saving"
          @click="lessonFormOpen = false"
        >
          取消
        </button>
      </div>
    </form>

    <!-- eslint-disable-next-line vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
    <p v-if="lessonDeletionNote" class="lp-muted">{{ lessonDeletionNote }}</p>

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
        {{ courseId === null ? '请选择课程查看课时' : '该课程还没有章节，先新增章节再添加课时' }}
      </p>
      <div
        v-for="chapter in chapters"
        v-else
        :key="chapter.chapterId"
        class="chapter"
      >
        <div class="chapter-head">
          <h2>{{ chapter.sort }}. {{ chapter.title }}</h2>
          <button
            class="lp-btn"
            :disabled="busy || saving || lessonFormOpen"
            @click="openLessonForm(chapter)"
          >
            {{ chapter.lessons.length === 0 ? '新增第一个课时' : '新增课时' }}
          </button>
        </div>
        <table class="lp-table">
          <thead>
            <tr>
              <th>课时</th>
              <th>时长</th>
              <th>编码</th>
              <th>分辨率</th>
              <th>大小</th>
              <th>转码</th>
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
              <td>{{ transcodeText(lesson) }}</td>
              <td>{{ lesson.hasVideo ? '已上传' : '未上传' }}</td>
              <td class="row-actions">
                <button
                  class="lp-btn"
                  :disabled="busy"
                  @click="openLessonEdit(chapter, lesson)"
                >
                  编辑
                </button>
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
                <button
                  v-if="courseSold === false"
                  class="lp-btn lp-btn--danger"
                  :disabled="busy || saving"
                  @click="removeLesson(chapter, lesson)"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="chapter.lessons.length === 0">
              <td
                colspan="8"
                class="lp-muted"
              >
                本章还没有课时，先新增课时再上传视频
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
.chapter-head { display: flex; align-items: center; gap: 12px; }
.form-card { margin-bottom: 16px; }
.form-card h2 { margin: 0 0 12px; font-size: 16px; }
.form-card .lp-field { max-width: 520px; }
.form-note { margin: 0 0 12px; }
.actions { display: flex; gap: 8px; }
.row-actions { display: flex; gap: 8px; }
.progress-card { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.progress-card progress { flex: 1; }
.preview-mask { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.55); z-index: 20; }
.preview-card { width: min(880px, 92vw); padding: 16px; border-radius: 8px; background: #fff; }
.preview-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.preview-card h2 { margin: 0; font-size: 16px; }
.preview-card video { width: 100%; max-height: 70vh; margin-top: 12px; background: #000; }
</style>
