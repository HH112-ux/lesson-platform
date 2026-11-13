/**
 * Teacher learning analytics endpoints of the console.
 *
 * <p>The payload mirrors the course-service records one to one. watched seconds are a lifetime
 * sum, never a window increment, and every completion rate uses learning students as its
 * denominator.</p>
 *
 * @author Caspian
 * @since 0.16.0
 */
import { apiRequest } from './client'

/** One point of the daily active series, missing days already filled with zero. */
export interface TeacherAnalyticsDailyPoint {
  /** Natural day, {@code yyyy-MM-dd}. */
  date: string
  /** Distinct active students, a lower bound. */
  studentCount: number
}

/** Teacher level summary of the learning dashboard. */
export interface TeacherAnalyticsOverview {
  /** Effective natural day window. */
  windowDays: number
  startDate: string
  endDate: string
  courseCount: number
  publishedCourseCount: number
  /** Students holding an active entitlement. */
  studentCount: number
  /** Students with at least one progress row. */
  learningStudentCount: number
  /** Students active in the window. */
  activeStudentCount: number
  newStudentCount: number
  revokedStudentCount: number
  /** Lifetime effective watch seconds. */
  cumulativeWatchedSeconds: number
  completedLessons: number
  /** Student-course pairs that finished every visible lesson. */
  completedCourseStudents: number
  /** Student-course pairs with at least one progress row. */
  learningCourseStudents: number
  /** Overall completion rate in percent, {@code null} without learners. */
  completionRatePercent: number | null
  lastLearnedAt: string | null
  dailyActiveStudents: TeacherAnalyticsDailyPoint[]
}

/** One row of the course analytics table. */
export interface TeacherAnalyticsCourseRow {
  courseId: number
  title: string
  /** 0 draft, 1 pending, 2 published, 3 offline. */
  status: number
  lessonCount: number
  studentCount: number
  learningStudentCount: number
  activeStudentCount: number
  newStudentCount: number
  revokedStudentCount: number
  /** Lifetime effective watch seconds. */
  cumulativeWatchedSeconds: number
  completedLessonCount: number
  completedCourseStudents: number
  /** Completion rate in percent over learning students, {@code null} without learners. */
  completionRatePercent: number | null
  lastLearnedAt: string | null
}

/** One page of owned courses. */
export interface TeacherAnalyticsPage {
  pageNum: number
  pageSize: number
  total: number
  records: TeacherAnalyticsCourseRow[]
}

/** One progress bucket of the six fixed buckets. */
export interface TeacherAnalyticsBucket {
  /** {@code not-started}, {@code 0-24}, {@code 25-49}, {@code 50-74}, {@code 75-99} or {@code 100}. */
  bucket: string
  studentCount: number
}

/** Completion counters of one lesson. */
export interface TeacherAnalyticsLesson {
  lessonId: number
  title: string
  learners: number
  completedStudents: number
  /** Completion rate in percent over learners, {@code null} without learners. */
  completionRatePercent: number | null
}

/** One course with its progress distribution and per-lesson completion. */
export interface TeacherAnalyticsDetail extends TeacherAnalyticsCourseRow {
  progressDistribution: TeacherAnalyticsBucket[]
  lessons: TeacherAnalyticsLesson[]
}

/**
 * Loads the teacher level summary.
 *
 * @param days natural day window, clamped by the backend to 1–365
 * @returns summary with the filled daily active series
 * @author Caspian
 * @since 0.16.0
 */
export function loadTeacherAnalyticsOverview(days: number): Promise<TeacherAnalyticsOverview> {
  return apiRequest<TeacherAnalyticsOverview>(
    `/api/v1/teacher/analytics/overview?days=${days}`,
  )
}

/**
 * Lists one page of the caller's own courses with their learning counters.
 *
 * @param days natural day window
 * @param pageNum one-based page number
 * @param pageSize page size, the backend caps it at 100
 * @returns one page ordered by course update time
 * @author Caspian
 * @since 0.16.0
 */
export function listTeacherAnalyticsCourses(
  days: number,
  pageNum: number,
  pageSize: number,
): Promise<TeacherAnalyticsPage> {
  const params = new URLSearchParams({
    days: String(days),
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  return apiRequest<TeacherAnalyticsPage>(
    `/api/v1/teacher/analytics/courses?${params.toString()}`,
  )
}

/**
 * Loads one owned course with its distribution and lesson rows.
 *
 * <p>A course of another teacher answers {@code 40301}; a missing course answers {@code 40401}.</p>
 *
 * @param courseId course identifier
 * @param days natural day window
 * @returns the detail of one owned course
 * @author Caspian
 * @since 0.16.0
 */
export function loadTeacherAnalyticsCourse(
  courseId: number,
  days: number,
): Promise<TeacherAnalyticsDetail> {
  return apiRequest<TeacherAnalyticsDetail>(
    `/api/v1/teacher/analytics/courses/${courseId}?days=${days}`,
  )
}
