/**
 * Operations endpoints served by lp-admin-service.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest } from './client'

/** One course waiting for the operations review decision. */
export interface PendingCourse {
  courseId: number
  title: string
  coverUrl?: string | null
  ownerTeacherId: number
  /** Current price in yuan. */
  price: number
  submittedAt: string
}

/** Page of the review queue, aligned with {@code AdminPendingCoursePageView}. */
export interface PendingCoursePage {
  records: PendingCourse[]
  total: number
  pageNum: number
  pageSize: number
}

/** Course state after one review decision. */
export interface ReviewResult {
  courseId: number
  /** 2 approved, 0 rejected. */
  status: number
  /** Price after the decision, in fen. */
  priceFen: number
  reviewedAt: string
}

/** Body of {@code POST /api/v1/admin/courses/{courseId}/review}. */
export interface ReviewPayload {
  approved: boolean
  /** Required when approving; the backend stores prices in fen. */
  priceFen?: number
  /** Required when rejecting. */
  remark?: string
}

/**
 * Lists the courses waiting for review, oldest submission first.
 *
 * @param pageNum one-based page number
 * @param pageSize page size, the backend caps it at 100
 * @returns the page of pending courses
 * @author Caspian
 * @since 0.2.0
 */
export function listPendingCourses(pageNum: number, pageSize: number): Promise<PendingCoursePage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  return apiRequest<PendingCoursePage>(`/api/v1/admin/courses/pending?${params.toString()}`)
}

/**
 * Approves or rejects a pending course.
 *
 * @param courseId course identifier
 * @param payload decision, price in fen when approving and remark when rejecting
 * @returns the course state after the decision
 * @author Caspian
 * @since 0.2.0
 */
export function reviewCourse(courseId: number, payload: ReviewPayload): Promise<ReviewResult> {
  return apiRequest<ReviewResult>(`/api/v1/admin/courses/${courseId}/review`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
