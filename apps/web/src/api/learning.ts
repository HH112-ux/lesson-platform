/**
 * Learning endpoints of the gateway.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest } from './client'

/** Offset page used by the learning service. */
export interface LearningPage<T> {
  pageNum: number
  pageSize: number
  total: number
  records: T[]
}

/** Course card of the personal learning list. */
export interface MyCourseCard {
  courseId: number
  title: string
  coverUrl?: string | null
  /** Watched share of the course, already rounded by the backend. */
  progressPercent: number
  /** Lesson to resume from; {@code null} before the first playback. */
  lastLessonId: number | null
  /** True when the order was refunded; access is revoked but progress stays visible. */
  refunded: boolean
}

/** Stored progress of one lesson. */
export interface LessonProgress {
  lessonId: number
  positionSeconds: number
  maxPositionSeconds: number
  watchedSeconds: number
  completed: boolean
  truncated: boolean
  updatedAt?: string | null
}

/** Progress of every lesson of a course. */
export interface CourseProgress {
  courseId: number
  lessons: LessonProgress[]
}

/** Signed playback URL issued for one lesson. */
export interface PlayToken {
  playUrl: string
  /** Lifetime in seconds. */
  expiresIn: number
  /** Expiry as epoch seconds. */
  expiresAt: number
}

/**
 * Loads the courses the signed-in user owns.
 *
 * @param pageNum 1-based page number
 * @param pageSize page size, capped by the backend at 100
 * @returns the page of owned courses
 * @author Caspian
 * @since 0.2.0
 */
export function listMyCourses(
  pageNum: number,
  pageSize: number,
): Promise<LearningPage<MyCourseCard>> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  return apiRequest<LearningPage<MyCourseCard>>(
    `/api/v1/learning/my-courses?${params.toString()}`,
  )
}

/**
 * Loads the stored progress of every lesson in a course.
 *
 * @param courseId course identifier
 * @returns the per-lesson progress
 * @author Caspian
 * @since 0.2.0
 */
export function getCourseProgress(courseId: number): Promise<CourseProgress> {
  const params = new URLSearchParams({ courseId: String(courseId) })
  return apiRequest<CourseProgress>(`/api/v1/learning/progress?${params.toString()}`)
}

/**
 * Reports a playback position; completion is decided by the backend.
 *
 * @param lessonId lesson being watched
 * @param positionSeconds current position
 * @returns the stored progress after the report
 * @author Caspian
 * @since 0.2.0
 */
export function reportProgress(
  lessonId: number,
  positionSeconds: number,
): Promise<LessonProgress> {
  return apiRequest<LessonProgress>('/api/v1/learning/progress', {
    method: 'POST',
    body: JSON.stringify({ lessonId, positionSeconds }),
  })
}

/**
 * Requests a signed playback URL for a lesson.
 *
 * @param lessonId lesson to play
 * @returns the signed URL and its lifetime
 * @author Caspian
 * @since 0.2.0
 */
export function getPlayToken(lessonId: number): Promise<PlayToken> {
  return apiRequest<PlayToken>(`/api/v1/lessons/${lessonId}/play-token`)
}
