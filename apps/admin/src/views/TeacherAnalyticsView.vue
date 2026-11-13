<script setup lang="ts">
/**
 * Teacher learning dashboard.
 *
 * <p>Every figure comes from the aggregated teacher analytics endpoints, never from a sample. The
 * window is fixed to thirty days, watched time is a lifetime sum and completion rates use learning
 * students as their denominator, as frozen by the 2026-11-13 decision.</p>
 *
 * @author Caspian
 * @since 0.16.0
 */
import { computed, onMounted, ref } from 'vue'
import {
  listTeacherAnalyticsCourses,
  loadTeacherAnalyticsCourse,
  loadTeacherAnalyticsOverview,
  type TeacherAnalyticsCourseRow,
  type TeacherAnalyticsDetail,
  type TeacherAnalyticsOverview,
  type TeacherAnalyticsPage,
} from '../api/teacherAnalytics'
import TrendChart from '../components/TrendChart.vue'
import { describeError } from '../utils/errors'
import { formatDuration, formatTime } from '../utils/format'

/** Window shown by the dashboard, in days. */
const WINDOW_DAYS = 30

/** Rows per page of the course table. */
const PAGE_SIZE = 8

/** Display text of the six fixed progress buckets. */
const BUCKET_LABELS: Record<string, string> = {
  'not-started': '未开始',
  '0-24': '1–24%',
  '25-49': '25–49%',
  '50-74': '50–74%',
  '75-99': '75–99%',
  '100': '已学完',
}

/** Display text of the course status codes. */
const STATUS_LABELS: Record<number, string> = {
  0: '草稿',
  1: '待审核',
  2: '已上架',
  3: '已下架',
}

const overview = ref<TeacherAnalyticsOverview | null>(null)
const page = ref<TeacherAnalyticsPage | null>(null)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')

const detail = ref<TeacherAnalyticsDetail | null>(null)
const detailId = ref<number | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const activePoints = computed(() =>
  (overview.value?.dailyActiveStudents ?? []).map((point) => ({
    label: point.date,
    value: point.studentCount,
  })),
)

const totalPages = computed(() => {
  const total = page.value?.total ?? 0
  return Math.max(1, Math.ceil(total / PAGE_SIZE))
})

const distribution = computed(() => {
  const buckets = detail.value?.progressDistribution ?? []
  const total = buckets.reduce((sum, item) => sum + item.studentCount, 0)
  return buckets.map((item) => ({
    bucket: item.bucket,
    label: BUCKET_LABELS[item.bucket] ?? item.bucket,
    studentCount: item.studentCount,
    width: total > 0 ? `${Math.max((item.studentCount / total) * 100, 1.5).toFixed(1)}%` : '0%',
  }))
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [summary, courses] = await Promise.all([
      loadTeacherAnalyticsOverview(WINDOW_DAYS),
      listTeacherAnalyticsCourses(WINDOW_DAYS, pageNum.value, PAGE_SIZE),
    ])
    overview.value = summary
    page.value = courses
  } catch (error) {
    overview.value = null
    page.value = null
    errorText.value = describeError(error, '学习数据加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCourses(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    page.value = await listTeacherAnalyticsCourses(WINDOW_DAYS, pageNum.value, PAGE_SIZE)
  } catch (error) {
    page.value = null
    errorText.value = describeError(error, '课程数据加载失败')
  } finally {
    loading.value = false
  }
}

function goPage(target: number): void {
  if (target < 1 || target > totalPages.value) {
    return
  }
  pageNum.value = target
  void loadCourses()
}

async function openDetail(row: TeacherAnalyticsCourseRow): Promise<void> {
  detailId.value = row.courseId
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await loadTeacherAnalyticsCourse(row.courseId, WINDOW_DAYS)
  } catch (error) {
    detailError.value = describeError(error, '课程详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  detailId.value = null
  detail.value = null
  detailError.value = ''
}

function formatCount(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value)
    ? Math.round(value).toLocaleString('zh-CN')
    : '--'
}

function rateText(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}%` : '--'
}

function statusText(status: number): string {
  return STATUS_LABELS[status] ?? `状态 ${status}`
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>学习数据</h1>
        <p class="page-sub">
          近 {{ WINDOW_DAYS }} 天 · 仅统计本人名下课程 · 观看时长为累计值
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
    <template v-else-if="overview">
      <div class="kpis">
        <article class="lp-card kpi">
          <p class="kpi-label">
            学员
          </p>
          <p class="kpi-value">
            {{ formatCount(overview.studentCount) }}
          </p>
          <p class="kpi-hint">
            在学 {{ formatCount(overview.learningStudentCount) }} 人 · 完课率分母口径
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            近 {{ overview.windowDays }} 天活跃
          </p>
          <p class="kpi-value">
            {{ formatCount(overview.activeStudentCount) }}
          </p>
          <p class="kpi-hint">
            新增 {{ formatCount(overview.newStudentCount) }} 人 · 退款失效
            {{ formatCount(overview.revokedStudentCount) }} 人
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            累计观看时长
          </p>
          <p class="kpi-value">
            {{ formatDuration(overview.cumulativeWatchedSeconds) }}
          </p>
          <p class="kpi-hint">
            累计值，非窗口增量；无播放心跳，暂不提供单次与按天时长
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            完课率
          </p>
          <p class="kpi-value">
            {{ rateText(overview.completionRatePercent) }}
          </p>
          <p class="kpi-hint">
            分母为在学学员 {{ formatCount(overview.learningCourseStudents) }} 人次 · 已完课
            {{ formatCount(overview.completedCourseStudents) }} 人次
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            已完成课时
          </p>
          <p class="kpi-value">
            {{ formatCount(overview.completedLessons) }}
          </p>
          <p class="kpi-hint">
            课程 {{ formatCount(overview.courseCount) }} 门 · 已上架
            {{ formatCount(overview.publishedCourseCount) }} 门
          </p>
        </article>

        <article class="lp-card kpi">
          <p class="kpi-label">
            最近学习
          </p>
          <p class="kpi-value kpi-value--time">
            {{ formatTime(overview.lastLearnedAt) }}
          </p>
          <p class="kpi-hint">
            窗口 {{ overview.startDate }} 至 {{ overview.endDate }}
          </p>
        </article>
      </div>

      <section class="lp-card chart-card">
        <header class="chart-head">
          <h2>每日活跃学员</h2>
          <span>近 {{ overview.windowDays }} 天 · 按学习记录更新日 · 活跃人数为下限</span>
        </header>
        <TrendChart
          :points="activePoints"
          kind="area"
          color="var(--lp-primary)"
          :format-value="formatCount"
          :aria-label="`近 ${WINDOW_DAYS} 天每日活跃学员`"
        />
      </section>

      <section class="lp-card">
        <header class="chart-head">
          <h2>课程明细</h2>
          <span>共 {{ formatCount(page?.total ?? 0) }} 门 · 点击查看完课分布</span>
        </header>
        <table
          v-if="page && page.records.length > 0"
          class="lp-table"
        >
          <thead>
            <tr>
              <th>课程</th>
              <th>状态</th>
              <th>学员</th>
              <th>在学</th>
              <th>活跃</th>
              <th>累计观看</th>
              <th>完课率</th>
              <th>最近学习</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in page.records"
              :key="row.courseId"
            >
              <td>{{ row.title || '未命名课程' }}</td>
              <td>{{ statusText(row.status) }}</td>
              <td>{{ formatCount(row.studentCount) }}</td>
              <td>{{ formatCount(row.learningStudentCount) }}</td>
              <td>{{ formatCount(row.activeStudentCount) }}</td>
              <td>{{ formatDuration(row.cumulativeWatchedSeconds) }}</td>
              <td>{{ rateText(row.completionRatePercent) }}</td>
              <td>{{ formatTime(row.lastLearnedAt) }}</td>
              <td>
                <button
                  class="lp-btn detail-btn"
                  @click="openDetail(row)"
                >
                  详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p
          v-else
          class="lp-empty"
        >
          暂无课程，创建并上架课程后即可看到学习数据
        </p>
        <footer
          v-if="page && page.total > PAGE_SIZE"
          class="pager"
        >
          <button
            class="lp-btn"
            :disabled="pageNum <= 1"
            @click="goPage(pageNum - 1)"
          >
            上一页
          </button>
          <span>第 {{ pageNum }} / {{ totalPages }} 页</span>
          <button
            class="lp-btn"
            :disabled="pageNum >= totalPages"
            @click="goPage(pageNum + 1)"
          >
            下一页
          </button>
        </footer>
      </section>

      <section
        v-if="detailId !== null"
        class="lp-card detail-card"
      >
        <header class="chart-head">
          <div>
            <h2>{{ detail?.title || '课程学习详情' }}</h2>
            <span v-if="detail">
              {{ detail.lessonCount }} 个可见课时 · 在学
              {{ formatCount(detail.learningStudentCount) }} 人 · 完课率
              {{ rateText(detail.completionRatePercent) }}
            </span>
          </div>
          <button
            class="lp-btn"
            @click="closeDetail"
          >
            关闭
          </button>
        </header>

        <p
          v-if="detailLoading"
          class="lp-empty"
        >
          加载中…
        </p>
        <p
          v-else-if="detailError"
          class="lp-empty"
        >
          {{ detailError }}
        </p>
        <template v-else-if="detail">
          <div class="distribution">
            <div
              v-for="item in distribution"
              :key="item.bucket"
              class="distribution-row"
            >
              <span class="distribution-label">{{ item.label }}</span>
              <div
                class="distribution-track"
                aria-hidden="true"
              >
                <span
                  class="distribution-fill"
                  :style="{ width: item.width }"
                />
              </div>
              <span class="distribution-value">{{ formatCount(item.studentCount) }} 人</span>
            </div>
          </div>

          <table
            v-if="detail.lessons.length > 0"
            class="lp-table"
          >
            <thead>
              <tr>
                <th>课时</th>
                <th>学习人数</th>
                <th>完课人数</th>
                <th>完课率</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="lesson in detail.lessons"
                :key="lesson.lessonId"
              >
                <td>{{ lesson.title }}</td>
                <td>{{ formatCount(lesson.learners) }}</td>
                <td>{{ formatCount(lesson.completedStudents) }}</td>
                <td>{{ rateText(lesson.completionRatePercent) }}</td>
              </tr>
            </tbody>
          </table>
          <p
            v-else
            class="lp-empty"
          >
            该课程暂无可见课时
          </p>
        </template>
      </section>

      <p class="footnote">
        说明：观看时长为累计值，不含窗口增量；完课率分母为在学学员；管理员仅能看到本人教师档案名下课程。
      </p>
    </template>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-sub {
  margin: 6px 0 0;
  color: var(--lp-muted);
  font-size: 13px;
}

h1 {
  margin: 0;
  font-size: 20px;
}

h2 {
  margin: 0;
  font-size: 15px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-label {
  margin: 0 0 8px;
  color: var(--lp-muted);
  font-size: 13px;
}

.kpi-value {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kpi-value--time {
  font-size: 18px;
}

.kpi-hint {
  margin: 0;
  color: var(--lp-muted);
  font-size: 12px;
  line-height: 1.6;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.chart-head span {
  color: var(--lp-muted);
  font-size: 12px;
}

.detail-btn {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
  color: var(--lp-muted);
  font-size: 13px;
}

.detail-card {
  margin-top: 16px;
}

.distribution {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.distribution-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.distribution-label {
  flex-shrink: 0;
  width: 64px;
  color: var(--lp-muted);
}

.distribution-track {
  flex: 1;
  overflow: hidden;
  height: 8px;
  border-radius: 999px;
  background: #f0f1f2;
}

.distribution-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--lp-primary);
}

.distribution-value {
  flex-shrink: 0;
  width: 64px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.footnote {
  margin: 16px 0 0;
  color: var(--lp-muted);
  font-size: 12px;
  line-height: 1.7;
}
</style>
