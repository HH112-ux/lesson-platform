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
  /** Amount in fen. */
  payAmountFen: number
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
  /** Gross merchandise volume in fen. */
  gmvFen: number
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

/** One refund waiting for or already holding an operations decision. */
export interface AdminRefundView {
  refundId: number
  refundNo: string
  orderNo: string
  userId: number
  /** 1 Alipay, 2 WeChat. */
  channel: number
  /** Refund amount in fen. */
  amountFen: number
  reason: string
  /** 0 pending, 1 approved, 2 refunded, 3 rejected, 4 failed. */
  status: number
  reviewReason?: string | null
  failReason?: string | null
  reviewedBy?: number | null
  reviewedAt?: string | null
  refundedAt?: string | null
  createdAt: string
}

/** Page of refunds. */
export interface AdminRefundPage {
  records: AdminRefundView[]
  total: number
  pageNum: number
  pageSize: number
}

/** Read-only channel snapshot of one refund. */
export interface AdminChannelRefundStatusView {
  refundId: number
  /** CONFIRMED, NOT_FOUND, OTHER, QUERY_FAILED or UNAVAILABLE. */
  channelOutcome: string
  channelRefundStatus?: string | null
  /** Channel refund amount in fen. */
  channelRefundAmountFen?: number | null
  channelRefundNo?: string | null
  queriedAt?: string | null
  /** Why the outcome is unknown; empty when the channel confirmed the refund. */
  queryMessage?: string | null
  /** 0 not scanned, 1 channel done, 2 not found, 3 other, 4 query failed, 5 payment not settled. */
  reconcileFinding: number
  reconcileCheckedAt?: string | null
  reconcileAttempts: number
}

/** Body of {@code POST /api/v1/admin/refunds/{refundId}/review}. */
export interface RefundReviewPayload {
  approved: boolean
  /** Required when rejecting. */
  rejectReason?: string
}

/**
 * Lists refunds with an optional status filter.
 *
 * @param status refund status, {@code undefined} for all statuses
 * @param pageNum one-based page number
 * @param pageSize page size
 * @returns the page of refunds
 * @author Caspian
 * @since 0.1.0
 */
export function listRefunds(
  status: number | undefined,
  pageNum: number,
  pageSize: number,
): Promise<AdminRefundPage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  if (status !== undefined) {
    params.set('status', String(status))
  }
  return apiRequest<AdminRefundPage>(`/api/v1/admin/refunds?${params.toString()}`)
}

/**
 * Approves or rejects one pending refund.
 *
 * @param refundId refund identifier
 * @param payload decision, with a required reason when rejecting
 * @returns the refund state after the decision
 * @author Caspian
 * @since 0.1.0
 */
export function reviewRefund(
  refundId: number,
  payload: RefundReviewPayload,
): Promise<AdminRefundView> {
  return apiRequest<AdminRefundView>(`/api/v1/admin/refunds/${refundId}/review`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * Reads one refund back from the channel without changing it.
 *
 * @param refundId refund identifier
 * @returns channel snapshot plus the local reconciliation bookkeeping
 * @author Caspian
 * @since 0.9.0
 */
export function loadRefundChannelStatus(
  refundId: number,
): Promise<AdminChannelRefundStatusView> {
  return apiRequest<AdminChannelRefundStatusView>(
    `/api/v1/admin/refunds/${refundId}/channel`,
  )
}
