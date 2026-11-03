/**
 * Shared constants, types and helpers used by the web and admin front ends.
 *
 * <p>This package holds presentation-free code only: no Vue imports, no browser APIs and no
 * backend calls, so both apps and their tests can consume it.</p>
 *
 * @author Caspian
 * @since 0.0.0
 */

/** Gateway health probe path exposed by lp-gateway on port 8080. */
export const GATEWAY_HEALTH_PATH = '/actuator/health'

/** Default page size for list APIs, aligned with AGENTS.md 11.4. */
export const DEFAULT_PAGE_SIZE = 20

/** Sort direction accepted by list APIs. */
export type SortOrder = 'asc' | 'desc'

/** Page query parameters shared by list APIs. */
export interface PageQuery {
  /** One-based page number. */
  pageNum: number
  /** Page size, capped at 100 by the backend. */
  pageSize: number
  /** Optional sort expression such as `createdAt,desc`. */
  sort?: string
}

/** Review states of back-office content. */
export type ReviewStatus = 'pending' | 'approved' | 'rejected'

/** One item waiting in or leaving the back-office review queue. */
export interface ReviewItem {
  /** Stable item identifier. */
  id: string
  /** Current review state. */
  status: ReviewStatus
}

/**
 * Counts review items in the given state.
 *
 * @param items review queue items
 * @param status state to count
 * @returns number of items currently in the given state
 * @author Caspian
 * @since 0.0.0
 */
export function countByStatus(items: readonly ReviewItem[], status: ReviewStatus): number {
  return items.filter((item) => item.status === status).length
}
