<script setup lang="ts">
/**
 * Course screen of the teacher console: create drafts and edit the proposed price.
 *
 * <p>Only draft (0) and offline (3) courses are editable, the backend answers 40901 otherwise.
 * Amounts are typed and sent in yuan; the list renders the fen values returned by the server, so
 * this screen never converts an amount before sending it.
 *
 * @author Caspian
 * @since 0.11.0
 */
import { onMounted, reactive, ref } from 'vue'
import {
  createTeacherCourse,
  listCategories,
  listTeacherCourses,
  submitTeacherCourse,
  updateTeacherCourse,
  type Category,
  type TeacherCourse,
  type TeacherCoursePayload,
} from '../api/teacher'
import { ApiError } from '../api/client'
import { describeError } from '../utils/errors'
import { formatFen } from '../utils/format'

const PAGE_SIZE = 50
const EDITABLE_STATUS: readonly number[] = [0, 3]
const STATUS_TEXT: Record<number, string> = { 0: '草稿', 1: '待审核', 2: '已上架', 3: '已下架' }
const STATUS_HINT: Record<number, string> = { 1: '待审核，不可改价', 2: '已上架，不可直接改价' }
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/

const courses = ref<TeacherCourse[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const saving = ref(false)
const submittingId = ref<number | null>(null)
const errorText = ref('')
const notice = ref('')
const editing = ref<TeacherCourse | null>(null)
const formOpen = ref(false)

const form = reactive({
  title: '',
  coverUrl: '',
  categoryId: null as number | null,
  price: '',
  originalPrice: '',
  clearOriginalPrice: false,
})

function isEditable(course: TeacherCourse): boolean {
  return EDITABLE_STATUS.includes(course.status)
}

/** Returns the amount in yuan, {@code null} when empty and {@code NaN} when malformed. */
function parseAmount(text: string): number | null {
  const trimmed = text.trim()
  if (!trimmed) {
    return null
  }
  return AMOUNT_PATTERN.test(trimmed) ? Number(trimmed) : Number.NaN
}

/** Maps a failure to text that tells the teacher which rule blocked the save. */
function saveErrorText(error: unknown): string {
  if (error instanceof ApiError && error.code === 40001) {
    return '填写内容不合法：价格需为非负数、最多两位小数，标题不超过 120 字'
  }
  if (error instanceof ApiError && error.code === 40901) {
    return '课程当前状态不可修改：仅草稿或已下架课程可改价'
  }
  return describeError(error)
}

/** Maps a failed submission to the rule the teacher has to satisfy first. */
function submitErrorText(error: unknown): string {
  if (error instanceof ApiError && error.code === 40001) {
    return '提交失败：每个可见课时都必须先上传并确认视频'
  }
  if (error instanceof ApiError && error.code === 40901) {
    return '提交失败：仅草稿或已下架课程可提交审核'
  }
  return describeError(error, '提交审核失败')
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await listTeacherCourses(undefined, 1, PAGE_SIZE)
    courses.value = page.items
  } catch (error) {
    courses.value = []
    errorText.value = describeError(error, '课程列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.title = ''
  form.coverUrl = ''
  form.categoryId = null
  form.price = ''
  form.originalPrice = ''
  form.clearOriginalPrice = false
}

function openCreate(): void {
  editing.value = null
  resetForm()
  errorText.value = ''
  notice.value = ''
  formOpen.value = true
}

/**
 * Opens the form for an editable course.
 *
 * <p>The price inputs stay empty on purpose: an empty amount means "keep the current price", so the
 * yuan field is never filled by converting the fen value returned by the list.
 */
function openEdit(course: TeacherCourse): void {
  resetForm()
  editing.value = course
  form.title = course.title
  form.coverUrl = course.coverUrl
  form.categoryId = course.categoryId
  errorText.value = ''
  notice.value = ''
  formOpen.value = true
}

function closeForm(): void {
  formOpen.value = false
  editing.value = null
  resetForm()
}

async function save(): Promise<void> {
  errorText.value = ''
  notice.value = ''
  const price = parseAmount(form.price)
  const originalPrice = parseAmount(form.originalPrice)
  if (!form.title.trim()) {
    errorText.value = '请填写课程标题'
  } else if (form.categoryId === null) {
    errorText.value = '请选择课程分类'
  } else if (!form.coverUrl.trim()) {
    errorText.value = '请填写封面图片地址'
  } else if (Number.isNaN(price)) {
    errorText.value = '拟定价需为非负数，最多两位小数'
  } else if (Number.isNaN(originalPrice)) {
    errorText.value = '划线价需为非负数，最多两位小数'
  } else if (!editing.value && price === null) {
    errorText.value = '请填写拟定价（元）'
  }
  if (errorText.value) {
    return
  }
  const payload: TeacherCoursePayload = {
    title: form.title.trim(),
    coverUrl: form.coverUrl.trim(),
    categoryId: form.categoryId ?? undefined,
  }
  if (price !== null) {
    payload.price = price
  }
  if (form.clearOriginalPrice) {
    payload.clearOriginalPrice = true
  } else if (originalPrice !== null) {
    payload.originalPrice = originalPrice
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateTeacherCourse(editing.value.courseId, payload)
      notice.value = '已保存，拟定价已提交；最终价由运营审核确定。'
    } else {
      await createTeacherCourse(payload)
      notice.value = '课程草稿已创建，拟定价已提交；最终价由运营审核确定。'
    }
    closeForm()
    await load()
  } catch (error) {
    errorText.value = saveErrorText(error)
  } finally {
    saving.value = false
  }
}

/**
 * Sends an editable course to the operations review queue.
 *
 * <p>This is the only path from draft to pending review, so the console keeps it next to the edit
 * action. The backend re-checks ownership, status and lesson videos.
 */
async function submitReview(course: TeacherCourse): Promise<void> {
  if (!window.confirm(`确认提交《${course.title}》审核？提交后进入待审核，审核前不可修改。`)) {
    return
  }
  submittingId.value = course.courseId
  errorText.value = ''
  notice.value = ''
  try {
    await submitTeacherCourse(course.courseId)
    notice.value = '已提交审核，运营将确认最终价。'
    await load()
  } catch (error) {
    errorText.value = submitErrorText(error)
  } finally {
    submittingId.value = null
  }
}

onMounted(() => {
  void load()
  listCategories()
    .then((items) => {
      categories.value = items
    })
    .catch((error: unknown) => {
      errorText.value = describeError(error, '课程分类加载失败')
    })
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1>课程管理</h1>
      <button
        v-if="!formOpen"
        class="lp-btn lp-btn--primary"
        type="button"
        @click="openCreate"
      >
        新建课程
      </button>
    </header>

    <p
      v-if="notice || errorText"
      class="lp-alert"
      :class="{ notice: !errorText }"
    >
      {{ errorText || notice }}
    </p>

    <form
      v-if="formOpen"
      class="lp-card form-card"
      @submit.prevent="save"
    >
      <h2>
        {{ editing ? `修改课程：${editing.title}（当前 ${formatFen(editing.price)} 元）` : '新建课程' }}
      </h2>
      <label class="lp-field">
        <span class="lp-label">课程标题</span>
        <input
          v-model="form.title"
          class="lp-input"
          maxlength="120"
        >
      </label>
      <label class="lp-field">
        <span class="lp-label">分类</span>
        <select
          v-model="form.categoryId"
          class="lp-input"
        >
          <option :value="null">
            请选择
          </option>
          <option
            v-for="item in categories"
            :key="item.categoryId"
            :value="item.categoryId"
          >
            {{ item.name }}
          </option>
        </select>
      </label>
      <label class="lp-field">
        <span class="lp-label">封面地址</span>
        <input
          v-model="form.coverUrl"
          class="lp-input"
          maxlength="255"
        >
      </label>
      <label class="lp-field">
        <span class="lp-label">拟定价（元，教师只提交拟定价，最终价由运营审核确定）</span>
        <input
          v-model="form.price"
          class="lp-input"
          inputmode="decimal"
          :placeholder="editing ? '留空表示不修改，例如 199.00' : '例如 199.00'"
        >
      </label>
      <label class="lp-field">
        <span class="lp-label">划线价（元，可选）</span>
        <input
          v-model="form.originalPrice"
          class="lp-input"
          inputmode="decimal"
          placeholder="例如 299.00"
          :disabled="form.clearOriginalPrice"
        >
      </label>
      <label
        v-if="editing && editing.originalPrice !== null"
        class="check-field"
      >
        <input
          v-model="form.clearOriginalPrice"
          type="checkbox"
        >
        <span>清除当前划线价（{{ formatFen(editing.originalPrice) }} 元）</span>
      </label>
      <div class="actions">
        <button
          class="lp-btn lp-btn--primary"
          type="submit"
          :disabled="saving"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button
          class="lp-btn"
          type="button"
          :disabled="saving"
          @click="closeForm"
        >
          取消
        </button>
      </div>
    </form>

    <div class="lp-card table-card">
      <p
        v-if="loading"
        class="lp-empty"
      >
        加载中…
      </p>
      <p
        v-else-if="courses.length === 0"
        class="lp-empty"
      >
        还没有课程，点击右上角「新建课程」提交第一门课程
      </p>
      <table
        v-else
        class="lp-table"
      >
        <thead>
          <tr>
            <th>课程</th>
            <th>状态</th>
            <th>拟定价（元）</th>
            <th>划线价（元）</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="course in courses"
            :key="course.courseId"
          >
            <td>
              <strong>{{ course.title }}</strong>
              <div
                v-if="course.reviewRemark"
                class="lp-muted small"
              >
                驳回原因：{{ course.reviewRemark }}
              </div>
            </td>
            <td>
              <span class="lp-tag">{{ STATUS_TEXT[course.status] ?? course.status }}</span>
            </td>
            <td>{{ formatFen(course.price) }}</td>
            <td>{{ course.originalPrice === null ? '--' : formatFen(course.originalPrice) }}</td>
            <td>
              <div class="row-actions">
                <template v-if="isEditable(course)">
                  <button
                    class="lp-btn"
                    type="button"
                    @click="openEdit(course)"
                  >
                    改价
                  </button>
                  <button
                    class="lp-btn lp-btn--primary"
                    type="button"
                    :disabled="submittingId !== null"
                    @click="submitReview(course)"
                  >
                    {{ submittingId === course.courseId ? '提交中…' : '提交审核' }}
                  </button>
                </template>
                <span
                  v-else
                  class="lp-muted small"
                >{{ STATUS_HINT[course.status] ?? '当前状态不可修改' }}</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
h1 { margin: 0; font-size: 20px; }
h2 { margin: 0 0 16px; font-size: 16px; }
.lp-alert { margin-bottom: 16px; }
.notice { border-color: #c8e6c9; background: #f1f8f1; color: #2e7d32; }
.form-card { margin-bottom: 16px; }
.form-card .lp-field { max-width: 520px; }
.small { font-size: 12px; }
.actions { display: flex; gap: 8px; }
.check-field { display: flex; align-items: center; gap: 8px; max-width: 520px; }
.row-actions { display: flex; gap: 8px; align-items: center; }
.table-card { padding: 0; overflow: hidden; }
</style>
