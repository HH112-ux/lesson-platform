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

/** User row of {@code GET /api/v1/admin/users}. */
export interface AdminUserView {
  userId: number
  username: string
  nickname: string
  phone?: string | null
  status: number
  /** Role codes such as {@code STUDENT} or {@code ADMIN}. */
  roles?: string[]
  createdAt: string
}

/** Page of users. */
export interface AdminUserPage {
  records: AdminUserView[]
  total: number
  pageNum: number
  pageSize: number
}

/** Order row of {@code GET /api/v1/admin/orders}. */
export interface AdminOrderView {
  orderNo: string
  userId: number
  status: number
  /** Amount in yuan. */
  payAmount: number
  createdAt: string
}

/** Page of orders. */
export interface AdminOrderPage {
  records: AdminOrderView[]
  total: number
  pageNum: number
  pageSize: number
}

/** Top selling course of the overview. */
export interface CourseSale {
  courseId: number
  title: string
  orderCount: number
}

/** Operations overview returned by {@code GET /api/v1/admin/metrics/overview}. */
export interface MetricsOverview {
  /** Gross merchandise volume in yuan. */
  gmv: number
  orderCount: number
  /** Paid orders divided by created orders. */
  paidRate: number
  newUsers: number
  courseSalesTop5: CourseSale[]
}

/**
 * Lists users with an optional keyword.
 *
 * @param keyword username, nickname or phone fragment
 * @param pageNum one-based page number
 * @param pageSize page size
 * @returns the page of users
 * @author Caspian
 * @since 0.2.0
 */
export function listUsers(keyword: string, pageNum: number, pageSize: number): Promise<AdminUserPage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  if (keyword.trim()) {
    params.set('keyword', keyword.trim())
  }
  return apiRequest<AdminUserPage>(`/api/v1/admin/users?${params.toString()}`)
}

/**
 * Lists orders with an optional order number filter.
 *
 * @param orderNo exact or partial order number, empty for all
 * @param pageNum one-based page number
 * @param pageSize page size
 * @returns the page of orders
 * @author Caspian
 * @since 0.2.0
 */
export function listAdminOrders(orderNo: string, pageNum: number, pageSize: number): Promise<AdminOrderPage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  if (orderNo.trim()) {
    params.set('orderNo', orderNo.trim())
  }
  return apiRequest<AdminOrderPage>(`/api/v1/admin/orders?${params.toString()}`)
}

/**
 * Loads the operations overview.
 *
 * @returns aggregated metrics of the given period
 * @author Caspian
 * @since 0.2.0
 */
export function loadOverview(): Promise<MetricsOverview> {
  return apiRequest<MetricsOverview>('/api/v1/admin/metrics/overview')
}
