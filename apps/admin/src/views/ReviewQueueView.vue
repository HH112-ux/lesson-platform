<script setup lang="ts">
/**
 * Course review queue of the operations console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { onMounted, reactive, ref } from 'vue'
import { listPendingCourses, reviewCourse, type PendingCourse } from '../api/admin'
import { describeError } from '../utils/errors'
import { formatTime, formatYuan } from '../utils/format'

const PAGE_SIZE = 20

const records = ref<PendingCourse[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')
const notice = ref('')
const busyId = ref<number | null>(null)

interface RowDraft {
  price: string
  remark: string
  rejecting: boolean
}

const drafts = reactive<Record<number, RowDraft>>({})

function draftOf(course: PendingCourse): RowDraft {
  if (!drafts[course.courseId]) {
    drafts[course.courseId] = {
      price: formatYuan(course.price === 0 ? 0 : course.price),
      remark: '',
      rejecting: false,
    }
  }
  return drafts[course.courseId]
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await listPendingCourses(pageNum.value, PAGE_SIZE)
    records.value = page.records
    total.value = page.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = describeError(error, '审核队列加载失败')
  } finally {
    loading.value = false
  }
}

async function approve(course: PendingCourse): Promise<void> {
  const draft = draftOf(course)
  const yuan = Number(draft.price)
  if (!Number.isFinite(yuan) || yuan < 0) {
    notice.value = '请输入正确的价格（元）'
    return
  }
  await submit(course, { approved: true, priceFen: Math.round(yuan * 100) })
}

async function reject(course: PendingCourse): Promise<void> {
  const draft = draftOf(course)
  if (!draft.rejecting) {
    draft.rejecting = true
    return
  }
  if (!draft.remark.trim()) {
    notice.value = '驳回时必须填写原因'
    return
  }
  await submit(course, { approved: false, remark: draft.remark.trim() })
}

async function submit(
  course: PendingCourse,
  payload: { approved: boolean; priceFen?: number; remark?: string },
): Promise<void> {
  busyId.value = course.courseId
  notice.value = ''
  try {
    await reviewCourse(course.courseId, payload)
    delete drafts[course.courseId]
    await load()
  } catch (error) {
    notice.value = describeError(error)
  } finally {
    busyId.value = null
  }
}

function changePage(next: number): void {
  if (next < 1 || next === pageNum.value) {
    return
  }
  pageNum.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>课程审核</h1>
        <p class="lp-muted">
          共 {{ total }} 门课程等待审核，按提交时间正序处理
        </p>
      </div>
      <button
        class="lp-btn"
        :disabled="loading"
        @click="load"
      >
        刷新
      </button>
    </header>

    <p
      v-if="notice"
      class="lp-alert"
    >
      {{ notice }}
    </p>

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
        v-else-if="records.length === 0"
        class="lp-empty"
      >
        暂无待审核课程
      </p>
      <table
        v-else
        class="lp-table"
      >
        <thead>
          <tr>
            <th>课程</th>
            <th>教师 ID</th>
            <th>当前价格（元）</th>
            <th>提交时间</th>
            <th>审核</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="course in records"
            :key="course.courseId"
          >
            <td>
              <strong>{{ course.title }}</strong>
              <div class="lp-muted small">
                #{{ course.courseId }}
              </div>
            </td>
            <td>{{ course.ownerTeacherId }}</td>
            <td>
              <input
                v-model="draftOf(course).price"
                class="lp-input price-input"
                inputmode="decimal"
              >
            </td>
            <td>{{ formatTime(course.submittedAt) }}</td>
            <td class="actions">
              <button
                class="lp-btn lp-btn--primary"
                :disabled="busyId === course.courseId"
                @click="approve(course)"
              >
                通过
              </button>
              <button
                class="lp-btn lp-btn--danger"
                :disabled="busyId === course.courseId"
                @click="reject(course)"
              >
                {{ draftOf(course).rejecting ? '确认驳回' : '驳回' }}
              </button>
              <input
                v-if="draftOf(course).rejecting"
                v-model="draftOf(course).remark"
                class="lp-input remark-input"
                placeholder="驳回原因（必填）"
              >
            </td>
          </tr>
        </tbody>
      </table>

      <footer
        v-if="!loading && !errorText && total > PAGE_SIZE"
        class="pager"
      >
        <button
          class="lp-btn"
          :disabled="pageNum <= 1"
          @click="changePage(pageNum - 1)"
        >
          上一页
        </button>
        <span class="lp-muted">第 {{ pageNum }} 页 / 共 {{ Math.ceil(total / PAGE_SIZE) }} 页</span>
        <button
          class="lp-btn"
          :disabled="pageNum * PAGE_SIZE >= total"
          @click="changePage(pageNum + 1)"
        >
          下一页
        </button>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

h1 {
  margin: 0 0 6px;
  font-size: 20px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.small {
  font-size: 12px;
}

.price-input {
  width: 110px;
}

.remark-input {
  width: 180px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
}
</style>
